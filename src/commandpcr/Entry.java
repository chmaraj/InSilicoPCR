package commandpcr;

import java.io.File;

import org.apache.commons.cli.*;

public class Entry {
	
	public static void main(String[] args) {
		
		Options options = new Options();
		
		Option input = new Option("i", "input", true, "The input file/directory containing the .fasta or .fastq sequence(s)");
		input.setRequired(true);
		options.addOption(input);
		
		Option output = new Option("o", "output", true, "The directory to contain the output");
		output.setRequired(true);
		options.addOption(output);
		
		Option primerInput = new Option("p", "primers", true, "The custom primer file containing the putative PCR primers");
		primerInput.setRequired(true);
		options.addOption(primerInput);
		
		Option numThreads = new Option("t", "threads", true, "The number of threads to use. Default is maximum number of processors available.");
		numThreads.setRequired(false);
		options.addOption(numThreads);
		
		Option numMismatches = new Option("m", "mismatches", true, "The number of mismatches permitted. Default is 0.");
		numMismatches.setRequired(false);
		options.addOption(numMismatches);
		
		CommandLineParser parser = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cmd = null;
		
		try {
			cmd = parser.parse(options, args);
		}catch(ParseException e) {
			System.out.println(e.getMessage());
			formatter.printHelp("java commandpcr/CommandMain -i () -o () -p () [-t ()] [-m ()]", options);
			
			System.exit(-1);
		}
		
		File inputFile = new File(cmd.getOptionValue("input"));
		File outDir = new File(cmd.getOptionValue("output"));
		File primerFile = new File(cmd.getOptionValue("primers"));
		int threads = Runtime.getRuntime().availableProcessors();
		int mismatches = 0;
		if(cmd.getOptionValue("threads") != null) {
			threads = Integer.parseInt(cmd.getOptionValue("threads"));
		}
		if(cmd.getOptionValue("mismatches") != null) {
			mismatches = Integer.parseInt(cmd.getOptionValue("mismatches"));
		}
		
		CommandMain main = new CommandMain(inputFile, outDir, primerFile, threads, mismatches);
		main.run();
	}
}
