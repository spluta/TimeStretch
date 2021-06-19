use crossbeam_utils::thread;
use clap::{App, Arg};
use rand::Rng;
use realfft::RealFftPlanner;
use rustfft::num_complex::Complex;
use std::f64::consts::PI;
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
    .arg(
        Arg::with_name("extreme")
        .short("e")
        .long("extreme")
        .takes_value(true)
        .help("In addition to the standard NessStretch (default 0), there are 3 extreme modes (more cpu) set by this flag. 
        1 - breaks the (9) slices spectral slices into 4 more slices, and correlates those independently
        2 - makes 4 versions of each frame and chooses the one that best correlates with the previous frame
        3 - combination of 1 and 2: breaks the (9) spectral slices into 2 more slices, processes each twice, and chooses the most correlated version per slice"),
    )
    .get_matches();
    
    let file_name = matches.value_of("file").unwrap_or("");
    if file_name.is_empty() {
        println!("No file name provided. Run 'ness_stretch -h' for help.");
        return;
    }
    println!("The input file is: {}", file_name);
    
    let dm_in = matches.value_of("mult").unwrap_or("20.0");
    let dur_mult: f64 = match dm_in.parse() {
        Ok(n) => n,
        Err(_) => {
            eprintln!("error: mult argument not an float");
            return;
        }
    };
    
    let e_in = matches.value_of("extreme").unwrap_or("0");
    let mut extreme: usize = match e_in.parse() {
        Ok(n) => n,
        Err(_) => {
            eprintln!("error: extreme argument not an integer");
            return;
        }
    };
    if extreme > 3 {extreme = 0};
    println!("Extreme setting set to: {}", extreme);
    
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
    
    println!("");
    println!("Will process up to a 10 channel audio file in parallel. Anything beyond 10 channels will result in a 10 channel file.");
    
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
    
    let channels = transpose(chunked);
    
    let channel0 = channels[0].clone();
    let mut channel1: Vec<f64> = vec![0.0; 0];
    let mut channel2: Vec<f64> = vec![0.0; 0];
    let mut channel3: Vec<f64> = vec![0.0; 0];
    let mut channel4: Vec<f64> = vec![0.0; 0];
    let mut channel5: Vec<f64> = vec![0.0; 0];
    let mut channel6: Vec<f64> = vec![0.0; 0];
    let mut channel7: Vec<f64> = vec![0.0; 0];
    let mut channel8: Vec<f64> = vec![0.0; 0];
    let mut channel9: Vec<f64> = vec![0.0; 0];
    
    if channels.len()>1 {channel1 = channels[1].clone()};
    if channels.len()>2 {channel2 = channels[2].clone()};
    if channels.len()>3 {channel3 = channels[3].clone()};
    if channels.len()>4 {channel4 = channels[4].clone()};
    if channels.len()>5 {channel5 = channels[5].clone()};
    if channels.len()>6 {channel6 = channels[6].clone()};
    if channels.len()>7 {channel7 = channels[7].clone()};
    if channels.len()>8 {channel8 = channels[8].clone()};
    if channels.len()>9 {channel9 = channels[9].clone()};
    
    let mut out_channels: Vec<Vec<f64>> =
    vec![vec![0.0_f64; 0]; sound_file.spec().channels as usize];
    
    let mut out_channel0: Vec<f64> = vec![0.0_f64; 0];
    let mut out_channel1: Vec<f64> = vec![0.0_f64; 0];
    let mut out_channel2: Vec<f64> = vec![0.0_f64; 0];
    let mut out_channel3: Vec<f64> = vec![0.0_f64; 0];
    let mut out_channel4: Vec<f64> = vec![0.0_f64; 0];
    let mut out_channel5: Vec<f64> = vec![0.0_f64; 0];
    let mut out_channel6: Vec<f64> = vec![0.0_f64; 0];
    let mut out_channel7: Vec<f64> = vec![0.0_f64; 0];
    let mut out_channel8: Vec<f64> = vec![0.0_f64; 0];
    let mut out_channel9: Vec<f64> = vec![0.0_f64; 0];
    
    let now = SystemTime::now();
    
    
    thread::scope(|s| {
        
        s.spawn(|_| {
            out_channel0 = process_channel(0, channel0, num_slices, dur_mult, extreme);
        });
        
        s.spawn(|_| {
            if channels.len()>1 { 
                out_channel1 = process_channel(1, channel1, num_slices, dur_mult, extreme)
            }
        });
        
        s.spawn(|_| {
            if channels.len()>2 { 
                out_channel2 = process_channel(2, channel2, num_slices, dur_mult, extreme)
            }
        });
        
        s.spawn(|_| {
            if channels.len()>3 { 
                out_channel3 = process_channel(3, channel3, num_slices, dur_mult, extreme)
            }
        });
        s.spawn(|_| {
            if channels.len()>4 { 
                out_channel4 = process_channel(4, channel4, num_slices, dur_mult, extreme)
            }
        });
        s.spawn(|_| {
            if channels.len()>5 { 
                out_channel5 = process_channel(5, channel5, num_slices, dur_mult, extreme)
            }
        });
        s.spawn(|_| {
            if channels.len()>6 { 
                out_channel6 = process_channel(6, channel6, num_slices, dur_mult, extreme)
            }
        });
        s.spawn(|_| {
            if channels.len()>7 { 
                out_channel7 = process_channel(7, channel7, num_slices, dur_mult, extreme)
            }
        });
        s.spawn(|_| {
            if channels.len()>8 { 
                out_channel8 = process_channel(8, channel8, num_slices, dur_mult, extreme)
            }
        });
        s.spawn(|_| {
            if channels.len()>9 { 
                out_channel9 = process_channel(9, channel9, num_slices, dur_mult, extreme)
            }
        });
        
        
    }).unwrap();
    
    println!("{:?}", now.elapsed());
    
    out_channels[0] = out_channel0;
    if channels.len()>1 {out_channels[1] = out_channel1};
    if channels.len()>2 {out_channels[2] = out_channel2};
    if channels.len()>3 {out_channels[3] = out_channel3};
    if channels.len()>4 {out_channels[4] = out_channel4};
    if channels.len()>5 {out_channels[5] = out_channel5};
    if channels.len()>6 {out_channels[6] = out_channel6};
    if channels.len()>7 {out_channels[7] = out_channel7};
    if channels.len()>8 {out_channels[8] = out_channel8};
    if channels.len()>9 {out_channels[9] = out_channel9}; 
    
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

fn process_microframe (spectrum:Vec<Complex<f64>>, last_framevec:Vec<f64>, filt_win: Vec<f64>) -> Vec<f64> {
    let win_len = (spectrum.len()-1)*2;
    
    let mut real_planner = RealFftPlanner::<f64>::new();
    
    let ifft = real_planner.plan_fft_inverse(win_len);
    let mut out_frame = ifft.make_output_vec();
    let mut flipped_frame = vec![0.0; win_len];
    
    let mut spectrum_out = real_planner.plan_fft_forward(win_len).make_output_vec();
    
    for iter in 0..spectrum.len() {
        let mut temp = spectrum[iter].to_polar();
        temp.0 = temp.0 * filt_win[iter];
        temp.1 = rand::thread_rng().gen_range(-PI/2.0..PI/2.0);
        spectrum_out[iter] = Complex::from_polar(temp.0, temp.1);
    }
    ifft.process(&mut spectrum_out, &mut out_frame).unwrap();
    
    let half_vec0 = &last_framevec[win_len/2..];
    let half_vec1 = &out_frame[..win_len/2];
    
    let mut correlation = 0.0;
    let temp_sum:f64 = half_vec0.iter().sum();
    if temp_sum != 0.0 {
        let r: f64 = half_vec0.iter().zip(half_vec1.iter()).map(|(x, y)| x * y).sum();
        let s: f64 = half_vec0.iter().zip(half_vec0.iter()).map(|(x, y)| x * y).sum();
        correlation = r/s;
    }
    
    for i in 0..win_len {
        if correlation<0.0 {
            flipped_frame[i] = -1.0*out_frame[i];
        } else {
            flipped_frame[i] = out_frame[i];
        }
    }
    correlation = correlation.abs();
    let ness_window = make_ness_window(win_len, correlation);
    let mut out_frame2 = vec![0.0; 3*win_len/2];
    for i in 0..(win_len/2) {
        out_frame2[i] = flipped_frame[i] * ness_window[i] + last_framevec[i + win_len/2] * ness_window[i + win_len/2];
        
    }
    for i in 0..win_len {
        out_frame2[i+win_len/2] = flipped_frame[i];
    }
    out_frame2.push(correlation);
    return out_frame2;
}

//,  sc: &crossbeam_utils::thread::Scope<'_>
fn process_channel (chan_num: usize, mut channel: Vec<f64>, num_slices: usize, dur_mult: f64, extreme: usize) -> Vec<f64> {    
    let mut win_lens = vec![0_usize; 0];
    
    let mut cut_offs = vec![vec![0.0_f64; 0]; num_slices];
    
    for iter in 0..num_slices {
        let size = i32::pow(2,iter as u32 + 8);
        win_lens.push(size as usize);
        let cutty:Vec<f64>;
        //add low_cut, then hi_cut
        if iter==(num_slices-1) {
            cutty = vec![1.0,32.0, 64.0, 96.0, 128.0];
        } else {cutty = vec![64.0, 80.0, 96.0, 112.0, 128.0];}
        cut_offs[iter] = cutty;
    }
    
    let mut indata = vec![0.0_f64; 65536];
    
    let in_size = channel.len() + 65536;
    
    indata.append(&mut channel);
    
    indata.append(&mut vec![0.0_f64; 2 * 65536 - (indata.len() % 65536)]);
    
    let mut outdata = vec![0.0_f64; (in_size as f64 * dur_mult + 65536.0) as usize];
    //all dependent on win_len ----
    
    for slice_num in 0..win_lens.len() {
        let mut cut_offs2 = vec![vec![0.0;2];0];
        println!("channel {} slice layer: {} of {}", chan_num, slice_num, win_lens.len());
        let win_len = win_lens[slice_num];
        
        let in_win = make_paul_window(win_len);
        
        let hop = (win_len as f64 / 2.0) / dur_mult;
        
        let mut stretch_points = vec![0; (in_size as f64 / hop) as usize];
        
        for iter in 0..stretch_points.len() {
            stretch_points[iter] = (hop * iter as f64) as i32 + (32768 - win_len / 2) as i32;
        }
        
        let mut out_points = vec![0; stretch_points.len()];
        
        for iter in 0..out_points.len() {
            out_points[iter] = (iter * win_len / 2)+ (32768 - win_len / 2);
        }
        
        let mut last_frame0 = vec![0.0; win_len];
        let mut last_frame1 = vec![0.0; win_len];
        let mut last_frame2 = vec![0.0; win_len];
        let mut last_frame3 = vec![0.0; win_len];
        
        let mut out_frame0 = vec![0.0; 3*win_len/2];
        let mut out_frame1 = vec![0.0; 3*win_len/2];
        let mut out_frame2 = vec![0.0; 3*win_len/2];
        let mut out_frame3 = vec![0.0; 3*win_len/2];
        
        for big_iter in 0..stretch_points.len() {
            let mut real_planner = RealFftPlanner::<f64>::new();
            let fft = real_planner.plan_fft_forward(win_len);
            let mut spectrum = fft.make_output_vec();
            let mut part = vec![0.0; win_len];
            for i in 0..win_len {
                part[i] = indata[(stretch_points[big_iter] + i as i32) as usize] * in_win[i];
            }
            fft.process(&mut part, &mut spectrum).unwrap();
            if extreme<1 {
                let filt_win = make_lr_bp_window(win_len/2+1, cut_offs[slice_num][0], cut_offs[slice_num][4], 64.0);
                
                out_frame0 = process_microframe(spectrum, last_frame0.clone(), filt_win);
                for i in 0..(win_len) {
                    last_frame0[i] = out_frame0[i+win_len/2];
                }
                for i in 0..(win_len/2) {
                    outdata[out_points[big_iter] as usize + i] += (out_frame0[i]+out_frame1[i]+out_frame2[i]+out_frame3[i])/ win_len as f64; //you have to divide by the sqrt
                }
            } else {
                thread::scope(|s| {
                    let spectrum0 = spectrum.clone();
                    let spectrum1 = spectrum.clone();
                    let spectrum2 = spectrum.clone();
                    let spectrum3 = spectrum.clone();
                    match extreme {
                        1 => {
                            for i in 0..4 {
                                cut_offs2.push(vec![cut_offs[slice_num][i], cut_offs[slice_num][i+1]])
                            }
                        }, 
                        2 => {
                            for _i in 0..4 {
                                cut_offs2.push(vec![cut_offs[slice_num][0], cut_offs[slice_num][4]])
                            }
                        },
                        3 => {
                            for i in 0..2 {
                                cut_offs2.push(vec![cut_offs[slice_num][i*2], cut_offs[slice_num][i*2+2]]);
                                cut_offs2.push(vec![cut_offs[slice_num][i*2], cut_offs[slice_num][i*2+2]]);
                            }
                        },
                        _ => {
                            std::process::exit(0x0100);
                        }
                    };
                    s.spawn(|_| {
                        let filt_win = make_lr_bp_window(win_len/2+1, cut_offs2[0][0], cut_offs2[0][1], 64.0);
                        out_frame0 = process_microframe(spectrum0, last_frame0.clone(), filt_win);
                        
                    });
                    s.spawn(|_| {
                        let filt_win = make_lr_bp_window(win_len/2+1, cut_offs2[1][0], cut_offs2[1][1], 64.0);
                        out_frame1 = process_microframe(spectrum1, last_frame1.clone(), filt_win);
                        // for i in 0..(win_len) {
                        //     last_frame1[i] = out_frame1[i+win_len/2];
                        // }
                    });
                    s.spawn(|_| {
                        let filt_win = make_lr_bp_window(win_len/2+1, cut_offs2[2][0], cut_offs2[2][1], 64.0);
                        out_frame2 = process_microframe(spectrum2, last_frame2.clone(), filt_win);
                        // for i in 0..(win_len) {
                        //     last_frame2[i] = out_frame2[i+win_len/2];
                        // }
                    });
                    s.spawn(|_| {
                        let filt_win = make_lr_bp_window(win_len/2+1, cut_offs2[3][0], cut_offs2[3][1], 64.0);
                        out_frame3 = process_microframe(spectrum3, last_frame3.clone(), filt_win);
                        // for i in 0..(win_len) {
                        //     last_frame3[i] = out_frame3[i+win_len/2];
                        // }
                    });
                }).unwrap();
                match extreme {
                    1 => {
                        for i in 0..(win_len/2) {
                            outdata[out_points[big_iter] as usize + i] += (out_frame0[i]+out_frame1[i]+out_frame2[i]+out_frame3[i])/ win_len as f64; //you have to divide by the sqrt
                        }
                        for i in 0..(win_len) {
                            last_frame0[i] = out_frame0[i+win_len/2];
                        }
                        for i in 0..(win_len) {
                            last_frame1[i] = out_frame1[i+win_len/2];
                        }
                        for i in 0..(win_len) {
                            last_frame2[i] = out_frame2[i+win_len/2];
                        }
                        for i in 0..(win_len) {
                            last_frame3[i] = out_frame3[i+win_len/2];
                        }
                    },
                    2 => {
                        let out_frames = [&out_frame0, &out_frame1, &out_frame2, &out_frame3]; 
                        let mut max = 2;
                        for i2 in 1..out_frames.len() {
                            if out_frames[i2][3*win_len/2]>out_frames[max][3*win_len/2] {max = i2};
                        }
                        //print!("{}",max);
                        for i in 0..(win_len/2) {
                            outdata[out_points[big_iter] as usize + i] += out_frames[max][i]/ win_len as f64; //you have to divide by the sqrt
                        }
                        for i in 0..(win_len) {
                            last_frame0[i] = out_frames[max][i+win_len/2];
                        }
                        for i in 0..(win_len) {
                            last_frame1[i] = out_frames[max][i+win_len/2];
                        }
                        for i in 0..(win_len) {
                            last_frame2[i] = out_frames[max][i+win_len/2];
                        }
                        for i in 0..(win_len) {
                            last_frame3[i] = out_frames[max][i+win_len/2];
                        }
                    },
                    3 => {
                        let out_frames = [&out_frame0, &out_frame1, &out_frame2, &out_frame3]; 
                        let mut max = [0,0];
                        if out_frames[0][3*win_len/2]>out_frames[1][3*win_len/2] {max[0] = 0} else {max[0] = 1};
                        if out_frames[2][3*win_len/2]>out_frames[3][3*win_len/2] {max[1] = 2} else {max[1] = 3};
                        for i in 0..(win_len/2) {
                            outdata[out_points[big_iter] as usize + i] += (out_frames[max[0]][i]+out_frames[max[1]][i])/ win_len as f64; //you have to divide by the sqrt
                        }
                        for i in 0..(win_len) {
                            last_frame0[i] = out_frames[max[0]][i+win_len/2];
                        }
                        for i in 0..(win_len) {
                            last_frame1[i] = out_frames[max[0]][i+win_len/2];
                        }
                        for i in 0..(win_len) {
                            last_frame2[i] = out_frames[max[1]][i+win_len/2];
                        }
                        for i in 0..(win_len) {
                            last_frame3[i] = out_frames[max[1]][i+win_len/2];
                        }
                    },
                    _ => {
                        std::process::exit(0x0100);
                    }
                }
                
            }
        };
    }
    return outdata;
}

fn normalize (mut chans: Vec<Vec<f64>>) -> Vec<Vec<f64>> {
    let mut max = 0.0;
    let chans_iter = chans.iter();
    for chan in chans_iter{
        for i2 in 0..chans[0].len() {
            if chan[i2]>max {max = chan[i2]}
        }
    }
    for chan_num in 0..chans.len(){
        for i2 in 0..chans[0].len() {
            chans[chan_num][i2] /= max;
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