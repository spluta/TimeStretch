use chrono::{DateTime, NaiveDateTime, TimeZone, Utc};
use clap::{App, Arg};
use rand::Rng;
use realfft::RealFftPlanner;
use rustfft::num_complex::Complex;
use std::env;
use std::f64::consts::PI;
use std::fs::File;
use std::iter::FromIterator;
use std::path::Path;
use std::time::SystemTime;

fn main() {
    let matches = App::new("NessStretch")
    .version("0.1.0")
    .author("Sam Pluta and Alex Ness")
    .about("NessStretch Time Stretching Algorithm")
    .arg(
        Arg::with_name("file")
        .short("f")
        .long("file")
        .takes_value(true)
        .help("An audio file you want to stretch. Must be a wav file. Can be of any bit depth, up to 32 bit float."),
    )
    .arg(
        Arg::with_name("mult")
        .short("m")
        .long("dur_mult")
        .takes_value(true)
        .help("The duration multiplier"),
    ).arg(
        Arg::with_name("out")
        .short("o")
        .long("out_file")
        .takes_value(true)
        .help("The name of the output file (optional - will name the file with the inputname_mult.wav"),
    )
    .arg(
        Arg::with_name("slices")
        .short("s")
        .long("num_slices")
        .takes_value(true)
        .help("The number of slices of the spectrum (optional - default is 9 - 4 is nice for transients/perc)"),
    )
    .get_matches();
    
    let file_name = matches.value_of("file").unwrap_or("sound/tng.wav");
    println!("The input file is: {}", file_name);
    
    let path = Path::new(file_name);
    let parent = path.parent();
    let file_stem = path.file_stem();
    let extension = path.extension();
    
    let dm_in = matches.value_of("mult").unwrap_or("20");
    let dur_mult: usize = match dm_in.parse() {
        Ok(n) => n,
        Err(_) => {
            eprintln!("error: mult argument not an integer");
            return;
        }
    };
    
    let s_in = matches.value_of("slices").unwrap_or("9");
    let num_slices: usize = match s_in.parse() {
        Ok(n) => n,
        Err(_) => {
            eprintln!("error: slices argument not an integer");
            return;
        }
    };
    println!("The audio file will be sliced into {} slices", num_slices);
    
    let temp_out_file = file_name.replace(".wav", &format!("{}{}{}", "_", dur_mult, ".wav"));
    let out_file = matches.value_of("out").unwrap_or(&temp_out_file);
    println!("The output file will be: {}", out_file);
    println!("");
    println!("If your output file exceeds the limitations of the WAV format, the OS will think the file is too short, but all of the data will be there and it will be readable with software like Reaper (by normal import) or Audacity (via import Raw Data)");
    
    let mut sound_file = hound::WavReader::open(file_name).unwrap();
    let mut intemp = vec![0.0; 0];
    
    if sound_file.spec().sample_format == hound::SampleFormat::Float {
        intemp.append(
            &mut sound_file
            .samples::<f32>()
            .map(|x| x.unwrap() as f64)
            .collect::<Vec<_>>(),
        );
    } else {
        intemp.append(
            &mut sound_file
            .samples::<i32>()
            .map(|x| x.unwrap() as f64)
            .collect::<Vec<_>>(),
        );
        let bits = sound_file.spec().bits_per_sample;
        for iter in 0..intemp.len() {
            intemp[iter] = intemp[iter] / (f64::powf(2.0, bits as f64));
        }
    };
    
    let chunked: Vec<Vec<f64>> = intemp
    .chunks(sound_file.spec().channels as usize)
    .map(|x| x.to_vec())
    .collect();
    
    let mut channels = transpose(chunked);
    
    let mut out_channels: Vec<Vec<f64>> =
    vec![vec![0.0_f64; 0]; sound_file.spec().channels as usize];
    //println!("{:?}", channels);
    //println!("{:?}", out_channels);
    
    let initial_len = intemp.len() / sound_file.spec().channels as usize;
    
    let now = SystemTime::now();
    let mut rng = rand::thread_rng();
    
    
    let mut win_lens = vec![0_usize; 0];
    let mut cut_offs = vec![vec![0.0_f64; 0]; num_slices];
    for iter in 0..num_slices {
        let size = i32::pow(2,iter as u32 + 8);
        win_lens.push(size as usize);
        let mut cutty = vec![0.0_f64; 0];
        //add low_cut, then hi_cut
        if iter==(num_slices-1) {
            cutty.push(1.0)
        } else {cutty.push(64.0)}
        cutty.push(128.0);
        cut_offs[iter] = cutty;
    }
    //println!("win_lens {:?}", win_lens);
    //println!("cut_offs {:?}", cut_offs);
    
    for channel in 0..sound_file.spec().channels {
        let mut indata = vec![0.0_f64; 65536];

        //println!("channel {}", channel);
        
        indata.append(&mut channels[channel as usize]);
        
        indata.append(&mut vec![0.0_f64; 2 * 65536 - (indata.len() % 65536)]);
        let mut outdata = vec![0.0_f64; (initial_len + 65536) * dur_mult + 65536];
        
        //all dependent on win_len ----
        
        for slice_num in 0..win_lens.len() {
        //for slice_num in 2..4 {    
            println!("channel {} slice layer: {} of {}", channel, slice_num, win_lens.len());
            let win_len = win_lens[slice_num];
            let mut part = vec![0.0; win_len];

            // make a planner
            let mut real_planner = RealFftPlanner::<f64>::new();
            let r2c = real_planner.plan_fft_forward(win_len);
            let mut spectrum = r2c.make_output_vec();
            
            let c2r = real_planner.plan_fft_inverse(win_len);
            let mut out_frame = c2r.make_output_vec();
            let mut out_frame2 = c2r.make_output_vec();
            let mut flipped_frame = vec![0.0; win_len+1];
            
            let in_win = make_paul_window(win_len);
            let filt_win = make_lr_bp_window(win_len/2+1, cut_offs[slice_num][0], cut_offs[slice_num][1], 64.0);
            //alternate brick wall ----->
            // let mut filt_win = vec![1.0; win_len/2+1];
            // for iter in 0..cut_offs[slice_num][0] as usize {
            //     filt_win[iter] = 0.0;
            // }
            // for iter in cut_offs[slice_num][1] as usize..filt_win.len() {
            //     filt_win[iter] = 0.0;
            // }

            let hop = (win_len as f32 / 2.0) / dur_mult as f32;
            
            //println!("{}, {}", "initial_length", initial_len);
            //println!("{}, {}, {}, {}, {}, {}", "hop", hop, "filt_win size", filt_win.len(), "win_len", win_len);
            //println!("{}", initial_len as f32 / hop);
            
            let mut stretch_points = vec![0; ((initial_len as f32+65536.0) / hop) as usize];
            
            for iter in 0..stretch_points.len() {
                stretch_points[iter] = (hop * iter as f32) as i32 + (32768 - win_len / 2) as i32;
            }
            
            let mut out_points = vec![0; stretch_points.len()];
            
            for iter in 0..out_points.len() {
                out_points[iter] = (iter * win_len / 2)+ (32768 - win_len / 2);
            }
            
            let mut last_framevec = vec![0.0; win_len];

            for big_iter in 0..stretch_points.len() {
                
                
                for i in 0..win_len {
                    part[i] = indata[(stretch_points[big_iter] + i as i32) as usize] * in_win[i];
                }
                
                r2c.process(&mut part, &mut spectrum).unwrap();
                //println!("{}", spectrum.len());
                //println!("{}", filt_win.len());
                for iter in 0..spectrum.len() {
                    let mut temp = spectrum[iter].to_polar();
                    temp.0 = temp.0 * filt_win[iter];
                    temp.1 = rng.gen_range(-PI/2.0..PI/2.0);
                    spectrum[iter] = Complex::from_polar(temp.0, temp.1);
                }
                c2r.process(&mut spectrum, &mut out_frame).unwrap();

                let half_vec0 = &last_framevec[win_len/2..];

                let half_vec1 = &out_frame[..win_len/2];

                let mut correlation:f64 = 0.0;
                let temp_sum:f64 = half_vec0.iter().sum();
                if temp_sum != 0.0 {
                    let r: f64 = half_vec0.iter().zip(half_vec1.iter()).map(|(x, y)| x * y).sum();
                    let s: f64 = half_vec0.iter().zip(half_vec0.iter()).map(|(x, y)| x * y).sum();
                    correlation = r/s;
                }
                //print!(" {}", correlation);

                for i in 0..win_len {
                    if correlation<0.0 {
                        flipped_frame[i] = -1.0*out_frame[i];
                    } else {
                        flipped_frame[i] = out_frame[i];
                    }
                }

                let ness_window = make_ness_window(win_len, correlation.abs());
                let mut out_frame2 = c2r.make_output_vec();
                for i in 0..(win_len/2) {
                    out_frame2[i] = flipped_frame[i] * ness_window[i] + last_framevec[i + win_len/2] * ness_window[i + win_len/2];
                }
                
                last_framevec = flipped_frame.clone();

                for i in 0..(win_len/2) {
                    outdata[out_points[big_iter] as usize + i] += out_frame2[i]/ win_len as f64; //you have to divide by the sqrt
                }
                
            }
        }
        out_channels[channel as usize] = outdata;
        println!("{:?}", now.elapsed());
    } //end of channel loop
    
    out_channels = normalize(out_channels);

    let spec = hound::WavSpec {
        channels: sound_file.spec().channels,
        sample_rate: sound_file.spec().sample_rate,
        bits_per_sample: 32,
        sample_format: hound::SampleFormat::Float,
    };
    
    let mut writer = hound::WavWriter::create(out_file, spec).unwrap();
    for samp in 0..(out_channels[0].len()) {
        for chan in 0..sound_file.spec().channels {
            writer
            .write_sample(out_channels[chan as usize][samp] as f32)
            .unwrap();
        }
    }
    writer.finalize().unwrap();
    
    println!("{:?}", now.elapsed());
}

fn normalize (mut chans: Vec<Vec<f64>>) -> Vec<Vec<f64>> {
    let mut max = 0.0;
    let chans_iter = chans.iter();
    for chan in chans_iter{
        for i2 in 0..chans[0].len() {
            if chan[i2]>max {max = chan[i2]}
        }
    }
    let chans_iter = chans.iter_mut();
    for chan in chans_iter{
        for i2 in 0..chan.len() {
            chan[i2] /= max;
        }
    }
    return chans;
}

fn make_ness_window(mut len: usize, correlation: f64) -> Vec<f64> {

    let mut floats: Vec<f64> = vec![0.0; len];
    let mut vals: Vec<f64> = vec![0.0; len];
  len = len-1;
  for iter in 0..(len) {
    floats[iter] = iter as f64 / (len as f64 / 2.0);
  }
  floats.push(0.0);
    for iter in 0..len {
        let fs = f64::powf((floats[iter]*PI/2.0).tan(), 2.0);
        vals [iter] = fs*(1.0/(1.0+(2.0*fs*(correlation))+f64::powf(fs, 2.0))).sqrt();
    }
    return vals
}

fn make_lr_lp_window(len: usize, hi_bin: f64, order: f64)-> Vec<f64> {
    let mut filter = vec![1.0; len];
    if hi_bin!=0.0 {
        for i in 0..len {
            filter[i] = 1.0/(1.0+(f64::powf(i as f64/hi_bin,order)));
        }
    }
    return filter;
}

fn make_lr_hp_window(len: usize, low_bin: f64, order: f64)-> Vec<f64> {
    let mut filter = vec![1.0; len];
    if low_bin!=0.0 {
        for i in 0..len {
            filter[i] = 1.0-(1.0/(1.0+(f64::powf(i as f64/low_bin,order))));
        }
    }
    return filter;
}

fn make_lr_bp_window(len: usize, low_bin: f64, hi_bin: f64, order: f64) -> Vec<f64> {
    let filter: Vec<f64>;
    if low_bin<=0.0 {
        filter = make_lr_lp_window(len, hi_bin, order);
    } else {
        if hi_bin>=(len-2) as f64{
            filter = make_lr_hp_window(len, low_bin, order);
        } else {
            let lp = make_lr_lp_window(len, hi_bin, order);
            let hp = make_lr_hp_window(len, low_bin, order);
            filter = lp.iter().zip(hp.iter()).map(|(x, y)| x * y).collect();
        }
    }
    return filter
}

fn make_paul_window(len: usize) -> Vec<f64> {
    let mut part = vec![0.0; len];
    for i in 0..len {
        let value = i as f64 / (len as f64 - 1.0) * 2.0 - 1.0;
        let value = f64::powf(1.0 - (f64::powf(value, 2.0)), 1.25);
        part[i] = value;
    }
    return part;
}

fn transpose<T>(v: Vec<Vec<T>>) -> Vec<Vec<T>>
where
T: Clone,
{
    assert!(!v.is_empty());
    (0..v[0].len())
    .map(|i| v.iter().map(|inner| inner[i].clone()).collect::<Vec<T>>())
    .collect()
}

// fn process_frame (indata: &Vec<f64>, spectrum: &Vec<Complex<T>>) -> Vec<T> {
    //     r2c.process(&mut indata, &mut spectrum).unwrap();
    
    //     println!("{:?}", spectrum);
    //     println!("{}", spectrum.len());
    
    //     //let mut temp = spectrum[0].clone();
    
    //     for iter in 1..spectrum.len() {
        //         let temp = Complex::new(spectrum[iter].re, rng.gen_range(-90.0f64..90.0f64));
        //         std::mem::replace(&mut spectrum[iter], temp);
        //     }
        //     println!("{}", "");
        //     println!("{}", "");
        
        //     println!("{:?}", spectrum);
        
        //     // create an iFFT and an output vector
        //     assert_eq!(outdata.len(), win_len);
        
        //     c2r.process(&mut spectrum, &mut outdata).unwrap();
        
        // }
