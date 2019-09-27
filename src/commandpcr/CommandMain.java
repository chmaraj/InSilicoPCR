package commandpcr;

import insilicopcr.Find;
import insilicopcr.MainRun;
import insilicopcr.Sample;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CommandMain {
	
	public static final String version = "v0.330";
	
	public static String sep = "/";
	
	private File inputFile = null, outDir = null, primerFile = null;
	private int threads = Runtime.getRuntime().availableProcessors();
	private File detailedDir, consolidatedDir;
	private File BBToolsLocation, BLASTLocation;
	private int mismatches = 0;
	private HashMap<String, String> primerDict = new HashMap<String, String>();
	private HashMap<String, Sample> sampleDict = new HashMap<String, Sample>();
	private boolean fastqPresent = false;
	private ThreadPoolExecutor mainPool;
	
	public CommandMain(File inputFile, File outDir, File primerFile, int threads, int mismatches) {
		this.inputFile = inputFile;
		this.outDir = outDir;
		this.primerFile = primerFile;
		this.threads = threads;
		this.mismatches = mismatches;
	}
	
	public void run() {
		RunPCRTask task = new RunPCRTask();
		Thread t = new Thread(task);
		t.setDaemon(true);
		t.start();
		try {
			t.join();
		}catch(InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	// Makes directories within the output directory
	public void makeDirectories() {
		detailedDir = new File(outDir.getAbsolutePath() + sep + "detailed_report");
		detailedDir.mkdirs();
		consolidatedDir = new File(outDir.getAbsolutePath() + sep + "consolidated_report");
		consolidatedDir.mkdirs();
	}
	
	// Find dependencies
	public void findDependencies() {
		String codeLocation = MainRun.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		String codeParent;
		if(System.getProperties().getProperty("os.name").contains("Windows")) {
			codeParent = String.join("/", Arrays.copyOfRange(codeLocation.split("/"), 1, codeLocation.split("/").length - 1));
		}else {
			codeParent = String.join("/", Arrays.copyOfRange(codeLocation.split("/"), 0, codeLocation.split("/").length - 1));
		}
		Path dir = Paths.get(codeParent);
//		Path dir = new File("C:\\Users\\ChmaraJ\\Desktop").toPath();
		Find.Finder finder = new Find.Finder("**bbmap", dir);
		for(Path path: finder.run()) {
			File directory = path.toFile();
			for(File file : directory.listFiles()) {
				if(file.getName().contains("tadpole.sh")) {
					BBToolsLocation = path.toFile();
				}
			}
		}
		
//		Path dir2 = new File("C:\\Users\\ChmaraJ\\Desktop").toPath();
		Find.Finder finder2;
		if(System.getProperties().getProperty("os.name").contains("Windows")) {
			finder2 = new Find.Finder("**makeblastdb.exe", dir);
		}else {
			finder2 = new Find.Finder("**makeblastdb", dir);
		}
		for(Path path : finder2.run()) {
			File blastdbpath = path.toFile();
			BLASTLocation = blastdbpath.getParentFile();
		}
		if(BBToolsLocation == null || BLASTLocation == null) {
			System.out.println("BBToolsLocation or BLASTLocation is null");
		}
	}
	
	// Main body of the pipeline, runs the contained methods in order
	public class RunPCRTask implements Runnable {
		
		public RunPCRTask() {
			
		}
		
		public void run() {
			
			long startTime = System.nanoTime();
			
			System.out.println("Beginning Program Run");
			findDependencies();
			System.out.println("Found Dependencies");
			makeDirectories();
			System.out.println("Created Directories");
			sampleDict = CommandMethods.createSampleDict(inputFile);
			System.out.println("Created Sample Dictionary");
			primerDict = CommandMethods.parseFastaToDictionary(primerFile);
			System.out.println("Created Primer Dictionary");
			CommandMethods.processPrimers(primerDict, outDir, sep);
			System.out.println("Finished Formatting Primers");
			// Check if any fastq files are present
			for(String key : sampleDict.keySet()) {
				if(sampleDict.get(key).getFileType().equals("fastq")) {
					System.out.println("FastQ files identified, conducting baiting and assembly");
					fastqPresent = true;
					break;
				}
			}
			if(fastqPresent) {
				runBaitTask();
				System.out.println("Completed First Baiting");
				runSecondBaitTask();
				System.out.println("Completed Second Baiting");
				runAssembleTask();
				System.out.println("Completed Assembly");
			}
			if(!System.getProperty("os.name").contains("Windows")) {
				CommandMethods.makeExecutable(BLASTLocation);
			}
			CommandMethods.makeBlastDB(new File(outDir.getAbsolutePath() + sep + "primer_tmp.fasta"), BLASTLocation);
			System.out.println("Completed Database Creation");
			// If files were fastq, need to use the assembly file instead of raw files
			runBLASTTask task = new runBLASTTask();
			Thread t = new Thread(task);
			t.setDaemon(true);
			t.start();
			try {
				t.join();
			}catch(InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("Completed BLAST");
			CommandMethods.addContigDict(sampleDict);
			CommandMethods.parseBlastOutput(consolidatedDir, detailedDir, primerDict, mismatches, sampleDict);
			System.out.println("Parsed BLAST output");
			CommandMethods.makeConsolidatedReport(consolidatedDir, sep, sampleDict, primerDict);
			System.out.println("Created Consolidated Report");
			CommandMethods.makeQALog(new File(outDir.getAbsolutePath() + sep + "QAlog.txt"), version, outDir, inputFile, primerFile, BBToolsLocation, BLASTLocation);
			
			long endTime = System.nanoTime();
			
			System.out.println("Done in " + Long.toString((endTime - startTime) / 1000000000) + " seconds");
		}
	}
	
	//Method to make a thread to run the CountMatchTask to prevent UI from hanging
	public void runBaitTask() {
		BaitTask task = new BaitTask();
		Thread t = new Thread(task);
		t.setDaemon(true);
		t.start();
		try {
			t.join();
		}catch(InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	//Method to make a thread to run the SeconBaitTask to prevent UI from hanging
	public void runSecondBaitTask() {
		SecondBaitTask task = new SecondBaitTask();
		Thread t = new Thread(task);
		t.setDaemon(true);
		t.start();
		try {
			t.join();
		}catch(InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	//Method to make a thread to run the AssemblyTask to prevent UI from hanging
	public void runAssembleTask() {
		AssembleTask task = new AssembleTask();
		Thread t = new Thread(task);
		t.setDaemon(true);
		t.start();
		try {
			t.join();
		}catch(InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public class runBLASTTask implements Runnable {
		
		public runBLASTTask() {
		}
		
		public void run() {
			mainPool = new ThreadPoolExecutor(threads, Integer.MAX_VALUE, Long.MAX_VALUE, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
			for(String key : sampleDict.keySet()) {
				if(sampleDict.get(key).getFileType().equals("fastq")) {
					BlastTask task = new BlastTask(outDir.getAbsolutePath() + sep + "primer_tmp.fasta", sampleDict.get(key).getAssemblyFile(),
							detailedDir, sep, threads, BLASTLocation);
					mainPool.submit(task);
				}else {
					for(String file : sampleDict.get(key).getFiles()) {
						BlastTask task = new BlastTask(outDir.getAbsolutePath() + sep + "primer_tmp.fasta", file,
								detailedDir, sep, threads, BLASTLocation);
						mainPool.submit(task);
					}
				}
			}
			try {
				mainPool.shutdown();
				mainPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
			}catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Bait FastQ reads from input files using BBDuk and the primer file as the target
	public class BaitTask implements Runnable {
		
		public BaitTask() {
		}
		
		public void run() {
			
			// Need to make sure that whatever k-value is being used is no longer than the shortest primer length
			int klength = Integer.MAX_VALUE;
			for(String key : primerDict.keySet()) {
				if(primerDict.get(key).length() < klength) {
					klength = primerDict.get(key).length();
				}
			}
			
			String ref = outDir.getAbsolutePath() + sep + "primer_tmp.fasta";
			
			for(String key : sampleDict.keySet()) {
				if(sampleDict.get(key).getFileType().equals("fastq")) {
					
					Sample currentSample = sampleDict.get(key);
					File sampleDir = new File(detailedDir.getAbsolutePath() + sep + currentSample.getName());
					sampleDir.mkdirs();
					String[] fullProcessCall;
					if(currentSample.getFiles().size() == 2) {
						fullProcessCall = new String[] {"java", "-ea", "-Xmx7g", "-cp", "./current", "jgi.BBDuk", "ref=" + ref, "k=" + klength,
								"in1=" + currentSample.getFiles().get(0), "in2=" + currentSample.getFiles().get(1), "hdist=" + mismatches,
								"threads=" + threads, "interleaved=t", "outm=" + sampleDir.getAbsolutePath() + sep + currentSample.getName() +
								"_targetMatches.fastq.gz"};
					}else {
						fullProcessCall = new String[] {"java", "-ea", "-Xmx7g", "-cp", "./current", "jgi.BBDuk", "ref=" + ref, "k=" + klength,
								"in=" + currentSample.getFiles().get(0), "hdist=" + mismatches, "threads=" + threads, "interleaved=t",
								"outm=" + sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_targetMatches.fastq.gz"};
					}
					try {
						Process p = Runtime.getRuntime().exec(fullProcessCall, null, BBToolsLocation);
						try {
							p.waitFor();
						}catch(InterruptedException e) {
							e.printStackTrace();
						}
					}catch(IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	// Bait more FastQ read nearby the originally baited reads using the originally baited reads as bait themselves
	// If USERS find issues with memory overflow, can use qhdist instead of hdist. Sacrifices speed for memory by 
	// Conducting mutations on query instead of reference? Dramatically reduces memory usage. 
	public class SecondBaitTask implements Runnable {
		
		public SecondBaitTask() {
		}
		
		public void run() {
			for(String key : sampleDict.keySet()) {
				if(sampleDict.get(key).getFileType().equals("fastq")) {
					Sample currentSample = sampleDict.get(key);
					File sampleDir = new File(detailedDir.getAbsolutePath() + sep + currentSample.getName());
					String ref = sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_targetMatches.fastq.gz";
					String[] fullProcessCall;
					if(currentSample.getFiles().size() == 2) {
						fullProcessCall = new String[] {"java", "-ea", "-Xmx7g", "-cp", "./current", "jgi.BBDuk", "ref=" + ref, 
								"in1=" + currentSample.getFiles().get(0), "in2=" + currentSample.getFiles().get(1), "hdist=" + mismatches,
								"threads=" + threads, "interleaved=t", "outm=" + sampleDir.getAbsolutePath() + sep + currentSample.getName() +
								"_doubleTargetMatches.fastq.gz"};
					}else {
						fullProcessCall = new String[] {"java", "-ea", "-Xmx7g", "-cp", "./current", "jgi.BBDuk", "ref=" + ref,
								"in=" + currentSample.getFiles().get(0), "hdist=" + mismatches, "threads=" + threads, "interleaved=t",
								"outm=" + sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_doubleTargetMatches.fastq.gz"};
					}
					try {
						Process p = Runtime.getRuntime().exec(fullProcessCall, null, BBToolsLocation);
						try {
							p.waitFor();
						}catch(InterruptedException e) {
							e.printStackTrace();
						}
					}catch(IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	// Assemble reads from both rounds of baiting to attempt to get long enough contigs to ensure as many primer hits are contained on the same contigs as possible
	public class AssembleTask implements Runnable {
		
		public AssembleTask() {
		}
		
		public void run() {
			for(String key : sampleDict.keySet()) {
				if(sampleDict.get(key).getFileType().equals("fastq")) {
					
					Sample currentSample = sampleDict.get(key);
					File sampleDir = new File(detailedDir.getAbsolutePath() + sep + currentSample.getName());
					
					String in = sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_doubleTargetMatches.fastq.gz";
					String out = sampleDir.getAbsolutePath() + sep + currentSample.getName() + "_assembly.fasta";
					
					// Make sure that the sample contains a reference to its own assembly file
					currentSample.setAssemblyFile(out);
					
					String[] fullProcessCall = {"java", "-ea", "-Xmx7g", "-cp", "./current", "assemble.Tadpole", 
							"in=" + in, "out=" + out, "threads=" + threads};
				
					try {
						Process p = Runtime.getRuntime().exec(fullProcessCall, null, BBToolsLocation);
						try {
							p.waitFor();
						}catch(InterruptedException e) {
							e.printStackTrace();
						}
					}catch(IOException e) {
						e.printStackTrace();
					}
					
				}
			}
		}
	}
	
	// Run Blast on the provided primers and query, calls addHeaderToTSV on the resulting .tsv file
	public class BlastTask implements Runnable {
		
		private String primers;
		private String query;
		private File detailedDir;
		private String sep;
		private File BLASTLocation;
		
		public BlastTask(String primers, String query, File detailedDir, String sep, int threads, File BLASTLocation) {
			this.primers = primers;
			this.query = query;
			this.detailedDir = detailedDir;
			this.sep = sep;
			this.BLASTLocation = BLASTLocation;
		}
		
		public void run() {
			
			File file = new File(query);
			String name = file.getName().split("_assembly\\.fasta")[0];
			name = name.split("\\.fasta")[0];
			name = name.split("\\.fna")[0];
			name = name.split("\\.ffn")[0];
			File blastOutput = new File(detailedDir.getAbsolutePath() + sep + name);
			blastOutput.mkdirs();
			File blastTSV = new File(blastOutput.getAbsolutePath() + sep + name + ".tsv");
			String[] windowsFullProcessCall = {BLASTLocation.getAbsolutePath() + sep + "blastn.exe", "-task", "blastn-short", "-query",
					query, "-db", primers, "-evalue", "1e-1", "-num_alignments", "1000000", "-num_threads", "1", "-outfmt", 
					"6 qseqid sseqid positive mismatch gaps evalue bitscore slen length qstart qend qseq sstart send sseq",
					"-out", blastTSV.getAbsolutePath()};
			String[] linuxFullProcessCall = {BLASTLocation.getAbsolutePath() + sep + "blastn", "-task", "blastn-short", "-query",
					query, "-db", primers, "-evalue", "1e-1", "-num_alignments", "1000000", "-num_threads", "1", "-outfmt", 
					"6 qseqid sseqid positive mismatch gaps evalue bitscore slen length qstart qend qseq sstart send sseq",
					"-out", blastTSV.getAbsolutePath()};
			try {
				Process p;
				if(System.getProperty("os.name").contains("Windows")) {
					p = Runtime.getRuntime().exec(windowsFullProcessCall);
				}else {
					p = Runtime.getRuntime().exec(linuxFullProcessCall);
				}
				try {
					p.waitFor();
				}catch(InterruptedException e) {
					e.printStackTrace();
				}
			}catch(IOException e) {
				e.printStackTrace();
			}
			CommandMethods.addHeaderToTSV(blastTSV);
		}
	}
}
