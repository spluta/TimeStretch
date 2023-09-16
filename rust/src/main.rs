//Rust NessStretch implementation
//Release under a GPLv3 license
//by Alex Ness and Sam Pluta

use clap::Parser;
//use klask::Settings;
use ness_stretch_lib::process_file;

#[derive(Parser)]
#[command(name = "ness_stretch")]
#[command(author = "Sam Pluta and Alex Ness")]
#[command(version = "0.4.3")]
#[command(about = "NessStretch Time Stretching Algorithm", long_about = "Time stretches a wav file (wav only) using the NessStretch time stretching algorith. Works with files of any duration, though things will get weird with files less than 65536 samples. Can time stretch to any duration your computer can handle!")]
//Sam Pluta and Alex Ness
struct Cli {
    /// An audio file you want to stretch. Must be a wav file. Can be of any bit depth, up to 32 bit float.
    #[arg(short, long)]
    file: String,

    /// The duration multiplier. eg: 100 is a 100x stretch. dur_mults under 1 might work...and they might not
    #[arg(short = 'd', long, default_value_t = 100.0)]
    duration_multiplier: f64,

    /// The name of the output file (optional - will name the file with the inputname_mult.wav if nothing is given)
    #[arg(short, long)]
    out_file: Option<String>,

    ///The number of slices of the spectrum (optional - default is 9 - 4 or 5 is nice for transients/perc).
    ///For below 88.2K, the max slice number is 9.
    ///For a file of 88.2K or over, the max slice number is 10."
    #[arg(short, long, default_value_t = 9)]
    slices: usize,

    ///In addition to the standard NessStretch (default 0), there are 3+ extreme modes (more cpu) set by this flag. 
    ///1 - makes 10 versions of each frame and chooses the one that best correlates with the previous frame
    ///2 - breaks the (9) spectral slices into 4 more slices, and correlates those independently
    ///3 - both 1 and 2, with the spectra split into 2 extra slices and 3 versions of each frame compared for correlation
    ///4+ - like extreme 1, except the number assigned is the number of versions of each frame made (-e 5 makes 5 versions of each frame)
    #[arg(short, long, default_value_t = 0)]
    extreme: usize,

    ///If the "slices" number is set to 1, we effectively have a PaulStretch.
    ///1 - makes the PaulStretch window length 8192
    ///2 - make the PaulStretch window length 16384
    ///3 - make the PaulStretch window length 32768
    #[arg(short='p', long, default_value_t = 1)]
    paulstretch_win_size: usize,

    ///Default is 1 - verbose. Setting to 0 disables posting to screen.
    #[arg(short, long, default_value_t = 1)]
    verbosity: usize,

    ///Default is 0 - full stretch, leaving a long tail at the end. Setting to >1 outputs only as many blocks as specified. Each block is 65536 samples long, so a value of 2 should result in an output of 131072 samples or about 3 seconds at 44100sr.
    #[arg(short = 'b', long, default_value_t = 0)]
    output_blocks: usize,

    ///Default is 1 - each window will be filtered. Setting to 0 assumes the input has been pre-filtered into bands.
    #[arg(short='g', long, default_value_t = 1)]
    filter_on: usize,
}


fn main() {
    //the main function
    //the top uses the clap crate to create flags and help
    
    let cli = Cli::parse();

    //klask::run_app(app, Settings::default(), |matches| {
    
        let file_name = cli.file;
        if file_name.is_empty() {
            println!("No file name provided. Run 'ness_stretch -h' for help.");
            return;
        }
        let dur_mult = cli.duration_multiplier;
        let mut extreme = cli.extreme;

        if extreme > 3 {
            extreme = 0
        };

        let num_slices: usize = cli.slices;
        let verbosity = cli.verbosity;
        let output_blocks = cli.output_blocks;
    
        let filter_on = cli.filter_on;
    
        let mut out_file = file_name.replace(".wav", &format!("{}{}{}", "_", dur_mult, ".wav"));
        if cli.out_file.is_some()
        {
            out_file = cli.out_file.unwrap();
        }


        if verbosity == 1 {
            println!("The input file is: {}", file_name);
            println!("Extreme setting set to: {}", extreme);
            println!("The output file will be: {}", out_file);
            println!("");
            println!("If your output file exceeds the limitations of the WAV format, the OS will think the file is too short, but all of the data will be there and it will be readable with software like Reaper (by normal import) or Audacity (via import Raw Data)");
    
            println!("");
            println!("Will process an audio file of any channel count in parallel.");
        }
    
        process_file(file_name, dur_mult, extreme, num_slices, output_blocks, verbosity, filter_on, cli.paulstretch_win_size, out_file)
 
}

