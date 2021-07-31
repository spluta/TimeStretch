//Rust NessStretch implementation
//Release under a GPLv3 license
//by Alex Ness and Sam Pluta

use crossbeam_utils::thread;
use clap::{App, Arg};
use rand::Rng;
use realfft::RealFftPlanner;
use rustfft::num_complex::Complex;
use std::f64::consts::PI;
use std::time::SystemTime;

const CHUNKS_IN_WRITE:usize = 500;
const MAX_SLICES:usize = 10;

fn main() {
    //the main function
    //the top uses the clap crate to create flags and help
    let matches = App::new("NessStretch")
    .version("0.4.0")
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
        .help("The duration multiplier. eg: 100 is a 100x stretch. dur_mults under 1 might work...and they might not"),
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
        .help("The number of slices of the spectrum (optional - default is 9 - 4 or 5 is nice for transients/perc).
        For below 88.2K, the max slice number is 9.
        For a file of 88.2K or over, the max slice number is 10."),
    )
    .arg(
        Arg::with_name("extreme")
        .short("e")
        .long("extreme")
        .takes_value(true)
        .help("In addition to the standard NessStretch (default 0), there are 3+ extreme modes (more cpu) set by this flag. 
        1 - makes 10 versions of each frame and chooses the one that best correlates with the previous frame
        2 - breaks the (9) spectral slices into 4 more slices, and correlates those independently
        3 - both 1 and 2, with the spectra split into 2 extra slices and 3 versions of each frame compared for correlation
        4+ - like extreme 1, except the number assigned is the number of versions of each frame made (-e 5 makes 5 versions of each frame)"
    ))
    .get_matches();
    
    
    let file_name = matches.value_of("file").unwrap_or("");
    if file_name.is_empty() {
        println!("No file name provided. Run 'ness_stretch -h' for help.");
        return;
    }
    println!("The input file is: {}", file_name);
    
    let dm_in = matches.value_of("mult").unwrap_or("100.0");
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
    let mut num_slices: usize = match s_in.parse() {
        Ok(n) => n,
        Err(_) => {
            eprintln!("error: slices argument not an integer");
            return;
        }
    };
    
    let temp_out_file = file_name.replace(".wav", &format!("{}{}{}", "_", dur_mult, ".wav"));
    let out_file = matches.value_of("out").unwrap_or(&temp_out_file);
    println!("The output file will be: {}", out_file);
    println!("");
    println!("If your output file exceeds the limitations of the WAV format, the OS will think the file is too short, but all of the data will be there and it will be readable with software like Reaper (by normal import) or Audacity (via import Raw Data)");
    
    println!("");
    println!("Will process an audio file of any channel count in parallel.");
    
    //reading the sound file using hound
    //only works with wav files - would be great to replace this with something that works with other formats
    
    let mut sound_file = hound::WavReader::open(file_name).unwrap();
    let mut intemp = vec![0.0; 0];
    
    //loads the sound file into intemp
    //checks to see the format of the sound file and converts all input (float, int16, int24, etc) to f64
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
    
    //if the sample rate is 88.2K or above, the largest window will be 131072, otherwise 65536
    let mut sr_mult = 1;
    let sample_rate = sound_file.spec().sample_rate;

    if sample_rate>=88200{
        sr_mult = 2;
    } else if sample_rate>=176400{
        sr_mult = 4;
    }
    let max_win_size:usize = 65536*sr_mult;

    //this is the size the frames that are calculated by process_chunk == 
    //[processed audio(max_win_size)][last_frame1][last_frame2][last_frame3][last_frame4] (for the 4 possible subslices of the slice)
    let out_frame_size:usize = max_win_size*3;  
    
    println!("Max Window Size: {}", max_win_size);
    
    //chunks the interleved file into chunks of size channels
    //then transposes the interleaved file into individual vectors for each channel
    let chunked: Vec<Vec<f64>> = intemp
    .chunks(sound_file.spec().channels as usize)
    .map(|x| x.to_vec())
    .collect();
    let mut channels = transpose(chunked);
    
    //then creates output vectors for each channel as well
    //let out_channels: Vec<Vec<f32>> = vec![vec![0.0_f32; 0]; sound_file.spec().channels as usize];
    
    let now = SystemTime::now();
    
    let mut win_lens = vec![0_usize; 0];  
    let mut hops = vec![0_f64; 0];  
    
    
    //the higher sample rates can have 10 slices
    if sample_rate<88200 && num_slices>9 {
        num_slices = MAX_SLICES-1;
    } else if sample_rate>=88200 && num_slices>9 {
        num_slices = MAX_SLICES;
    }
    println!("The audio file will be sliced into {} slices", num_slices);

    //pushes the win_lens into the vector
    //256 is always the smallest win_lens, 131072 always the largest (the extras just don't get used)
    for iter in 0..MAX_SLICES {
        let size = u32::pow(2, 8+iter as u32);
        win_lens.push(size as usize);
        hops.push((size as f64 / 2.0) / dur_mult); 
    }

    //creates a vector of fft cutoff bins based on the number of spectral slices
    //the extreme versions can split those cuttoffs into 2 and 4 more subslices
    let cut_max = max_win_size as f64/512.0;
    let mut cut_offs = vec![vec![0.0_f64; 0]; num_slices];
    for iter in 0..num_slices {
        let cutty:Vec<f64>;
        //add low_cut, then hi_cut
        if iter==(num_slices-1) {
            cutty = vec![1.0,cut_max/4.0, cut_max/2.0, 3.0*cut_max/4.0, cut_max];
        } else {cutty = vec![cut_max/2.0, 5.0*cut_max/8.0, 3.0*cut_max/4.0, 7.0*cut_max/8.0, cut_max];}
        cut_offs[iter] = cutty;
    }
    
    //right here just replace the calculated values with the csv values
    
    println!("window sizes {:?}", &win_lens[0..num_slices]);
    println!("spectral cut offs {:?}", cut_offs);
    
    let num_channels = channels.len();
    
    let mut indata = vec![vec![0.0_f64; max_win_size]; num_channels]; //0 pads the front of the channel with a max_frame of 0s
    let in_size = max_win_size+channels[0].len();
    
    let frames_to_add = 2 * max_win_size - (in_size % max_win_size);
    //println!("framestoadd {}", frames_to_add);
    for i in 0..num_channels {
        indata[i].append(&mut channels[i]); //adds the channel data to indata
        indata[i].append(&mut vec![0.0_f64; frames_to_add]);  //adds 0s at the end of indata so that the vector size is divisible by max_win_size
        // println!("indatasize {}", indata[i].len());
    }
    
    //creates the outdata vector as a indata*dur_mul plus an extra window for the last frame
    //let mut out_data = vec![vec![0.0_f64; (in_size as f64 * dur_mult + max_win_size as f64) as usize]; num_channels];
    let mut out_data = vec![vec![0.0_f64; max_win_size*CHUNKS_IN_WRITE]; num_channels];
    //set the hop size to calculate the chunks of audio to be processed
    //let chunk_size = max_win_size * 100;
    //let mut outdata_chunk = vec![0.0_f64; chunk_size];
    let mut chunk_points = vec![0; (in_size as f64 / max_win_size as f64*dur_mult) as usize];
    for iter in 0..chunk_points.len() {
        chunk_points[iter] = ((iter*max_win_size) as f64 /dur_mult) as usize;
    }

    while chunk_points.len()<4 {
        chunk_points.push(((chunk_points.len()*max_win_size) as f64 /dur_mult) as usize);
        for i in 0..num_channels {
            indata[i].append(&mut vec![0.0_f64; max_win_size]);
        }
    }
    
    //again, really ugly, but necessary for the parallel processing in the extreme versions of the algorithm
    //makes 4 different vectors - 1 for each of the 4 micro-slices of the spectral slice
    let mut last_frame0 = vec![0.0; win_lens[0]*2*num_channels];
    let mut last_frame1 = vec![0.0; win_lens[1]*2*num_channels];
    let mut last_frame2 = vec![0.0; win_lens[2]*2*num_channels];
    let mut last_frame3 = vec![0.0; win_lens[3]*2*num_channels];
    let mut last_frame4 = vec![0.0; win_lens[4]*2*num_channels];
    let mut last_frame5 = vec![0.0; win_lens[5]*2*num_channels];
    let mut last_frame6 = vec![0.0; win_lens[6]*2*num_channels];
    let mut last_frame7 = vec![0.0; win_lens[7]*2*num_channels];
    let mut last_frame8 = vec![0.0; win_lens[8]*2*num_channels];
    let mut last_frame9 = vec![0.0; win_lens[9]*2*num_channels];
    
    let mut out_temp0 = vec![0.0; out_frame_size];
    let mut out_temp1 = vec![0.0; out_frame_size];
    let mut out_temp2 = vec![0.0; out_frame_size];
    let mut out_temp3 = vec![0.0; out_frame_size];
    let mut out_temp4 = vec![0.0; out_frame_size];
    let mut out_temp5 = vec![0.0; out_frame_size];
    let mut out_temp6 = vec![0.0; out_frame_size];
    let mut out_temp7 = vec![0.0; out_frame_size];
    let mut out_temp8 = vec![0.0; out_frame_size];
    let mut out_temp9 = vec![0.0; out_frame_size];
    
    let mut out_frame0 = vec![0.0; out_frame_size*num_channels];
    let mut out_frame1 = vec![0.0; out_frame_size*num_channels];
    let mut out_frame2 = vec![0.0; out_frame_size*num_channels];
    let mut out_frame3 = vec![0.0; out_frame_size*num_channels];
    let mut out_frame4 = vec![0.0; out_frame_size*num_channels];
    let mut out_frame5 = vec![0.0; out_frame_size*num_channels];
    let mut out_frame6 = vec![0.0; out_frame_size*num_channels];
    let mut out_frame7 = vec![0.0; out_frame_size*num_channels];
    let mut out_frame8 = vec![0.0; out_frame_size*num_channels];
    let mut out_frame9 = vec![0.0; out_frame_size*num_channels];
    
    //let mut write_point;
    
    let spec = hound::WavSpec {
        channels: num_channels as u16,
        sample_rate: sample_rate,
        bits_per_sample: 32,
        sample_format: hound::SampleFormat::Float,
    };
    
    let mut writer = hound::WavWriter::create(out_file, spec).unwrap();
    
    for iter in 0..chunk_points.len() {
        if iter%25 == 0 {println!("chunk {} of {}", iter, chunk_points.len())}

        let chunk_point = chunk_points[iter];
        
        thread::scope(|s| {
            s.spawn(|_| {
                for chan_num in 0..num_channels {
                    out_temp0 = process_chunk(&indata[chan_num], chunk_point, win_lens[0], hops[0], cut_offs[0].clone(), last_frame0.clone(), chan_num, extreme, max_win_size, out_frame_size);
                    
                    for i in 0..(win_lens[0]*2) {
                        last_frame0[chan_num*win_lens[0]*2+i] = out_temp0[max_win_size+i];
                    }
                    for i in 0..max_win_size {
                        out_frame0[chan_num*max_win_size+i] = out_temp0[i];
                    }
                }
            });
            if num_slices>1 {
                s.spawn(|_| {
                    for chan_num in 0..num_channels {
                        out_temp1 = process_chunk(&indata[chan_num], chunk_point, win_lens[1], hops[1], cut_offs[1].clone(), last_frame1.clone(), chan_num, extreme, max_win_size, out_frame_size);
                        for i in 0..(win_lens[1]*2) {
                            last_frame1[chan_num*win_lens[1]*2+i] = out_temp1[max_win_size+i];
                        }
                        for i in 0..max_win_size {
                            out_frame1[chan_num*max_win_size+i] = out_temp1[i];
                        }
                    }
                });
            };
            if num_slices>2{
                s.spawn(|_| {
                    for chan_num in 0..num_channels {
                        out_temp2 = process_chunk(&indata[chan_num], chunk_point, win_lens[2], hops[2], cut_offs[2].clone(), last_frame2.clone(), chan_num, extreme, max_win_size, out_frame_size);
                        for i in 0..(win_lens[2]*2) {
                            last_frame2[chan_num*win_lens[2]*2+i] = out_temp2[max_win_size+i];
                        }
                        for i in 0..max_win_size {
                            out_frame2[chan_num*max_win_size+i] = out_temp2[i];
                        }
                    }
                });
            };
            if num_slices>3{
                s.spawn(|_| {
                    for chan_num in 0..num_channels {
                        out_temp3 = process_chunk(&indata[chan_num], chunk_point, win_lens[3], hops[3], cut_offs[3].clone(), last_frame3.clone(), chan_num, extreme, max_win_size, out_frame_size);
                        for i in 0..(win_lens[3]*2) {
                            last_frame3[chan_num*win_lens[3]*2+i] = out_temp3[max_win_size+i];
                        }
                        for i in 0..max_win_size {
                            out_frame3[chan_num*max_win_size+i] = out_temp3[i];
                        }
                    }
                });
            };
            if num_slices>4{
                s.spawn(|_| {
                    for chan_num in 0..num_channels {
                        out_temp4 = process_chunk(&indata[chan_num], chunk_point, win_lens[4], hops[4], cut_offs[4].clone(), last_frame4.clone(), chan_num, extreme, max_win_size, out_frame_size);
                        for i in 0..(win_lens[4]*2) {
                            last_frame4[chan_num*win_lens[4]*2+i] = out_temp4[max_win_size+i];
                        }
                        for i in 0..max_win_size {
                            out_frame4[chan_num*max_win_size+i] = out_temp4[i];
                        }
                    }
                });
            };
            if num_slices>5{
                s.spawn(|_| {
                    for chan_num in 0..num_channels {
                        out_temp5 = process_chunk(&indata[chan_num], chunk_point, win_lens[5], hops[5], cut_offs[5].clone(), last_frame5.clone(), chan_num, extreme, max_win_size, out_frame_size);
                        for i in 0..(win_lens[5]*2) {
                            last_frame5[chan_num*win_lens[5]*2+i] = out_temp5[max_win_size+i];
                        }
                        for i in 0..max_win_size {
                            out_frame5[chan_num*max_win_size+i] = out_temp5[i];
                        }
                    }
                });
            };
            if num_slices>6{
                s.spawn(|_| {
                    for chan_num in 0..num_channels {
                        out_temp6 = process_chunk(&indata[chan_num], chunk_point, win_lens[6], hops[6], cut_offs[6].clone(), last_frame6.clone(), chan_num, extreme, max_win_size, out_frame_size);
                        for i in 0..(win_lens[6]*2) {
                            last_frame6[chan_num*win_lens[6]*2+i] = out_temp6[max_win_size+i];
                        }
                        for i in 0..max_win_size {
                            out_frame6[chan_num*max_win_size+i] = out_temp6[i];
                        }
                    }
                });
            };
            if num_slices>7{
                s.spawn(|_| {
                    for chan_num in 0..num_channels {
                        out_temp7 = process_chunk(&indata[chan_num], chunk_point, win_lens[7], hops[7], cut_offs[7].clone(), last_frame7.clone(), chan_num, extreme, max_win_size, out_frame_size);
                        for i in 0..(win_lens[7]*2) {
                            last_frame7[chan_num*win_lens[7]*2+i] = out_temp7[max_win_size+i];
                        }
                        for i in 0..max_win_size {
                            out_frame7[chan_num*max_win_size+i] = out_temp7[i];
                        }
                    }
                });
            };
            if num_slices>8{
                s.spawn(|_| {
                    for chan_num in 0..num_channels {
                        out_temp8 = process_chunk(&indata[chan_num], chunk_point, win_lens[8], hops[8], cut_offs[8].clone(), last_frame8.clone(), chan_num, extreme, max_win_size, out_frame_size);
                        for i in 0..(win_lens[8]*2) {
                            last_frame8[chan_num*win_lens[8]*2+i] = out_temp8[max_win_size+i];
                        }
                        for i in 0..max_win_size {
                            out_frame8[chan_num*max_win_size+i] = out_temp8[i];
                        }
                    }
                });
            };
            if num_slices>9{
                s.spawn(|_| {
                    for chan_num in 0..num_channels {
                        out_temp9 = process_chunk(&indata[chan_num], chunk_point, win_lens[9], hops[9], cut_offs[9].clone(), last_frame9.clone(), chan_num, extreme, max_win_size, out_frame_size);
                        for i in 0..(win_lens[9]*2) {
                            last_frame9[chan_num*win_lens[9]*2+i] = out_temp9[max_win_size+i];
                        }
                        for i in 0..max_win_size {
                            out_frame9[chan_num*max_win_size+i] = out_temp9[i];
                        }
                    }
                });
            };
        }).unwrap();
        
        let write_point = (iter%CHUNKS_IN_WRITE)*max_win_size;
        
        for chan_num in 0..num_channels {
            let read_point = chan_num*max_win_size;
            for i in 0..max_win_size {
                out_data[chan_num][write_point+i] = 
                out_frame0[read_point+i]+out_frame1[read_point+i]+out_frame2[read_point+i]+out_frame3[read_point+i]
                +out_frame4[read_point+i]+out_frame5[read_point+i]+out_frame6[read_point+i]+out_frame7[read_point+i]
                +out_frame8[read_point+i]+out_frame9[read_point+i];
            }
        }
        
        if iter>0 && iter%CHUNKS_IN_WRITE==CHUNKS_IN_WRITE-1 {
            for samp in 0..max_win_size*CHUNKS_IN_WRITE {
                for chan in 0..num_channels {
                    writer
                    .write_sample(out_data[chan][samp] as f32)
                    .unwrap();
                    out_data[chan][samp] = 0.0;
                }
            }
        }
        
        if iter==chunk_points.len()-1 {
            for samp in 0..max_win_size*(iter%CHUNKS_IN_WRITE) {
                for chan in 0..num_channels {
                    writer
                    .write_sample(out_data[chan][samp] as f32)
                    .unwrap();
                }
            }
        }
        
    }
    
    //close the output file
    writer.finalize().unwrap();
    println!("{:?}", now.elapsed());
}
//this is the code that does the actual randomizing of phases
fn process_microframe (spectrum:Vec<Complex<f64>>, last_frame:&[f64], filt_win: Vec<f64>, extreme: usize) -> Vec<f64> {
    
    let half_win_len = spectrum.len()-1;
    let win_len = half_win_len*2;
    
    //sets up the ifft planner
    let mut real_planner = RealFftPlanner::<f64>::new();
    let ifft = real_planner.plan_fft_inverse(win_len);
    let mut out_frame = ifft.make_output_vec();
    let mut fin_out_frame = ifft.make_output_vec();
    let mut flipped_frame = vec![0.0; win_len];
    let mut spectrum_out = real_planner.plan_fft_forward(win_len).make_output_vec();
    
    let mut correlation = 0.0;
    let mut corr_temp = 0.0;
    let mut corr_abs;
    let mut c_a_temp = 0.0;
    let mut num_ffts = 1;
    if extreme == 1 {num_ffts = 10}
    if extreme == 3 {num_ffts = 3}
    if extreme > 3 {num_ffts = extreme}
    for _count in 0..num_ffts {
        
        //0s the bins and randomizes the phases
        for iter in 0..spectrum.len() {
            let mut temp = spectrum[iter].to_polar();
            temp.0 = temp.0 * filt_win[iter];
            temp.1 = rand::thread_rng().gen_range(-PI/2.0..PI/2.0);
            spectrum_out[iter] = Complex::from_polar(temp.0, temp.1);
        }
        //performs the ifft
        ifft.process(&mut spectrum_out, &mut out_frame).unwrap();
        
        //gets half the frame and checks correlation with the previous frame
        //let half_vec0 = &last_frame[win_len/2..];
        let half_current_frame = &out_frame[..half_win_len];
        
        let temp_sum:f64 = last_frame.iter().sum();
        if temp_sum != 0.0 {
            let r: f64 = last_frame.iter().zip(half_current_frame.iter()).map(|(x, y)| x * y).sum();
            let s: f64 = last_frame.iter().zip(last_frame.iter()).map(|(x, y)| x * y).sum();
            corr_temp = r/s;
        }
        corr_abs = corr_temp.abs();
        
        if corr_abs>c_a_temp {
            correlation = corr_temp;
            c_a_temp = correlation.abs();
            fin_out_frame = out_frame.clone();
        }
    }
    corr_abs = correlation.abs();
    if correlation==0.0 {fin_out_frame = out_frame.clone()}
    //if print {println!("correlation {}", correlation)}
    //inverts the randomized signal if the correlation is negative
    for i in 0..win_len {
        if correlation<0.0 {
            flipped_frame[i] = -1.0*fin_out_frame[i];
        } else {
            flipped_frame[i] = fin_out_frame[i];
        }
    }
    
    let ness_window = make_ness_window(win_len, corr_abs);
    
    //returns a frame that contains the frame multiplied by the ness_window, the flipped frame (necessary for checking the next correlation), and then the value of the correlation (which is used in extreme algorithms 2 and 3) 
    let mut out_frame2 = vec![0.0; win_len];
    
    //multiples the ness_window by the frame
    for i in 0..half_win_len {
        out_frame2[i] = flipped_frame[i] * ness_window[i] + (last_frame[i] * ness_window[i + half_win_len]);
    }
    for i in 0..half_win_len {
        out_frame2[i+half_win_len] = flipped_frame[i+half_win_len];
    }
    
    return out_frame2;
}

fn process_chunk(indata: &Vec<f64>, chunk_point: usize, win_len: usize, hop: f64, mut cut_offs: Vec<f64>, mut last_frame: Vec<f64>, chan_num: usize, mut extreme: usize, max_win_size: usize, out_frame_size: usize) -> Vec<f64> {

    let half_win_len = win_len/2;
    let in_win = make_paul_window(win_len);  //uses a paul window on the input
    
    let mut stretch_points = vec![0; (max_win_size/half_win_len) as usize];
    for iter in 0..stretch_points.len() {
        stretch_points[iter] = chunk_point+(hop * iter as f64) as usize + (max_win_size/2 - half_win_len) as usize;
    }
    let mut out_points = vec![0; stretch_points.len()];
    
    for iter in 0..out_points.len() {
        out_points[iter] = iter * half_win_len;
    }
    
    let mut out_chunk = vec![0.0; out_frame_size];
    
    let chan_point = chan_num*win_len*2;
    
    //big loop over the stretch points
    for big_iter in 0..stretch_points.len() {
        
        //for efficiency, does the fft once for the 1-4 micro-slices of the algorithm
        let mut real_planner = RealFftPlanner::<f64>::new();
        let fft = real_planner.plan_fft_forward(win_len);
        let mut spectrum = fft.make_output_vec();
        let mut part = vec![0.0; win_len];
        for i in 0..win_len {
            part[i] = indata[stretch_points[big_iter] + i] * in_win[i];
        }
        fft.process(&mut part, &mut spectrum).unwrap();
        
        let mut loops = 1;
        
        match extreme {
            0 => {
                cut_offs[1] = cut_offs[4];
            },
            1 => {
                cut_offs[1] = cut_offs[4];
            },
            2 => {
                loops = 4;
            },
            3 => {
                loops = 2;
                cut_offs[1] = cut_offs[2];
                cut_offs[2] = cut_offs[4];
            },
            _ => {
                cut_offs[1] = cut_offs[4];
                if extreme<4 {extreme = 10}
            }   
        }
        
        
        //let last_frame_out = vec![0.0; win_len*2];
        
        for i in 0..loops {
            let filt_win = make_lr_bp_window(half_win_len+1, cut_offs[i], cut_offs[i+1], 64.0);
            let last_frame_slice = &last_frame[chan_point+i*half_win_len..chan_point+(i+1)*half_win_len];
            //if big_iter==0 {println!("lfs {} {:?}", chan_num, &last_frame_slice[0..5])}
            //process_microframe does the actual processing of the phase and returns the phase randomized frame
            let out_frame = process_microframe(spectrum.clone(), last_frame_slice, filt_win, extreme);
            
            //get the current frame to return as the last 
            for i2 in 0..half_win_len {
                last_frame[chan_point+i*half_win_len+i2] = out_frame[half_win_len+i2];
            }
            //put the half frame sound output into the out_data starting at the outpoints
            let out_spot = out_points[big_iter] as usize;
            for i2 in 0..half_win_len {
                out_chunk[out_spot + i2] += out_frame[i2]/win_len as f64;
            }
        }
    };
    for i in 0..win_len*2 {
        out_chunk[max_win_size+i] = last_frame[chan_point+i];
    }
    return out_chunk;
}

//makes the ness window in accordance with the correlation number provided
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

//makes the linkwitz-riley fft crossfade window, which effectively 0s out the bins wanted in the spectral slice
//high pass, low pass, and bandbass versions
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

//the paul stretch window is used on input - might a well be a sine or hann window
fn make_paul_window(len: usize) -> Vec<f64> {
    let mut part = vec![0.0; len];
    for i in 0..len {
        let value = i as f64 / (len as f64 - 1.0) * 2.0 - 1.0;
        let value = f64::powf(1.0 - (f64::powf(value, 2.0)), 1.25);
        part[i] = value;
    }
    return part;
}

//flops a channel array from interleaved clusters to separate files
fn transpose<T>(v: Vec<Vec<T>>) -> Vec<Vec<T>>
where
T: Clone,
{
    assert!(!v.is_empty());
    (0..v[0].len())
    .map(|i| v.iter().map(|inner| inner[i].clone()).collect::<Vec<T>>())
    .collect()
}


