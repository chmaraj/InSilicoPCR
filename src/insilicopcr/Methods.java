package insilicopcr;

import javafx.scene.Scene;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import java.util.Arrays;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

public class Methods {
	
	private static HashMap<Character, Character[]> degenerates = new HashMap<Character, Character[]>();
	private static Pattern degenRegex;
	
	// Used to ensure a file is in fasta format, at least that it starts with a ">"
	public static boolean verifyFastaFormat(File checkFile) {
		String line;
		BufferedReader reader;
		try {
			String[] fileParts = checkFile.getName().split("\\.");
			
			// User can submit gzipped files, so if they do, read it appropriately
			if(fileParts[fileParts.length - 1].equals("gz")) {
				reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(checkFile))));
			}else {
				reader = new BufferedReader(new FileReader(checkFile));
			}
			try {
				line = reader.readLine();
				char[] characters = line.toCharArray();
				if(characters[0] == '>' || characters[0] == '@') {
					reader.close();
					return true;
				}
				reader.close();
			}catch(IOException e) {
				e.printStackTrace();
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
		return false;
	}
	
	// Create a list of samples
	public static HashMap<String, Sample> createSampleDict(File inputFile) {
		HashMap<String, Sample> sampleDict = new HashMap<String, Sample>();
		if(inputFile.isDirectory()) {
			for(File entry : inputFile.listFiles()) {
				String entryName = entry.getName().split("\\.fasta")[0];
				entryName = entryName.split("\\.fastq")[0];
				entryName = entryName.replaceAll("_R1", "");
				entryName = entryName.replaceAll("_R2", "");
				if(!sampleDict.isEmpty()) {
					boolean unique = true;
					for(String key : sampleDict.keySet()) {
						Sample checkSample = sampleDict.get(key);
						if(checkSample.getName().equals(entryName)) {
							unique = false;
							checkSample.addFile(entry.getAbsolutePath()); // Add the additional file path to the sample's file list
							// Attempt to ensure the file list has R1 and R2 in the correct order
							ArrayList<String> filesList = checkSample.getFiles();
							Collections.sort(filesList);
							checkSample.setFiles(filesList);							
							break;
						}
					}
					if(unique) {
						Sample sample = new Sample();
						sample.setName(entryName);
						if(entry.getName().contains(".fasta")) {
							sample.setFileType("fasta");
						}else if(entry.getName().contains(".fastq")) {
							sample.setFileType("fastq");
						}
						sample.addFile(entry.getAbsolutePath()); // First instance of sample, add the file path to the new sample's file list
						sampleDict.put(entryName, sample);
					}
				}else {
					Sample sample = new Sample();
					sample.setName(entryName);
					if(entry.getName().contains(".fasta")) {
						sample.setFileType("fasta");
					}else if(entry.getName().contains(".fastq")) {
						sample.setFileType("fastq");
					}
					sample.addFile(entry.getAbsolutePath()); // First file checked, add the file path to the new sample's file list
					sampleDict.put(entryName, sample);
				}
			}
		}else {
			Sample sample = new Sample();
			String sampleName = inputFile.getName().split("\\.")[0];
			sample.setName(sampleName);
			sample.setFile(inputFile.getAbsolutePath());
			if(inputFile.getName().contains(".fasta")) {
				sample.setFileType("fasta");
			}else if(inputFile.getName().contains(".fastq")) {
				sample.setFileType("fastq");
			}
			sampleDict.put(sampleName, sample);
		}
		return sampleDict;
	}
	
	// Parse a fasta file into a dictionary, where the ID is the key value for the sequence
	public static HashMap<String, String> parseFastaToDictionary(File file){
		
		HashMap<String, String> fastaDict = new HashMap<String, String>();
		
		// First read in all lines
		String line;
		ArrayList<String> lines = new ArrayList<String>();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))){
			try {
				while((line = reader.readLine()) != null) {
					if(!line.isEmpty()) {
						lines.add(line);
					}
				}
			}catch(IOException e) {
				e.printStackTrace();
			}
			reader.close();
		}catch(IOException e) {
			e.printStackTrace();
		}
		// This will recreate the file in memory
		String joinedLines = String.join("\n", lines);
		
		//Split into entries
		String[] splitEntries = joinedLines.split(">");
		for(String entry : splitEntries) {
			
			//Get rid of first entry from the split, it will contain nothing
			if(!entry.isEmpty()) {
				String[] splitEntry = entry.split("\n");
				String id = splitEntry[0].trim();
				String seq = splitEntry[1].trim();
				
				// Put the entry into the dictionary
				fastaDict.put(id, seq);
			}
		}
		
		// Return the filled dictionary
		return fastaDict;
	}
	
	// Process the primers in the primer dictionary
	public static void processPrimers(HashMap<String, String> primerDict, TextArea outputField, File outDir, String sep) {
		
		// Need to generate the degen Regex
		// Unfortunately cannot convert directly from Object[] to char[], or even from Character[] to char[]
		degenerates.put('R', new Character[] {'A', 'G'});
		degenerates.put('Y', new Character[] {'C', 'T'});
		degenerates.put('S', new Character[] {'G', 'C'});
		degenerates.put('W', new Character[] {'A', 'T'});
		degenerates.put('K', new Character[] {'G', 'T'});
		degenerates.put('M', new Character[] {'A', 'C'});
		degenerates.put('B', new Character[] {'G', 'C', 'T'});
		degenerates.put('D', new Character[] {'A', 'G', 'T'});
		degenerates.put('H', new Character[] {'A', 'C', 'T'});
		degenerates.put('V', new Character[] {'A', 'C', 'G'});
		degenerates.put('N', new Character[] {'A', 'C', 'G', 'T'});
		Character[] degenCharArray = degenerates.keySet().toArray(new Character[degenerates.keySet().size()]);
		char[] charDegen = new char[degenCharArray.length];
		for(int i = 0; i < charDegen.length; i++) {
			charDegen[i] = (char)degenCharArray[i];
		}
		String degenRegexString = String.join("", new String(charDegen));
		degenRegex = Pattern.compile("[" + degenRegexString + "]");
		
		// This regex will find any incompatible characters in the primer sequences
		Pattern regex = Pattern.compile("[^ATCGRYSWKMBDHVN]");
		
		// Have to make a deep copy of the primerDict keys, otherwise we get a reference that changes when we change the primerDict
		ArrayList<String> keySet = new ArrayList<String>();
		for(String key : primerDict.keySet()) {
			keySet.add(key);
		}
		for(String key : keySet) {
			String id = key;
			String seq = primerDict.get(id);
			
			// Check for illegal bases
			Matcher matcher = regex.matcher(seq);
			if(matcher.find()) {
				logMessage(outputField, "Primer sequence contains incompatible characters:\n" + id + "\n" + seq);
				return;
			}
			
			// If the sequence contains degenerated bases, create sequences for all possible iterations
			Matcher degenMatcher = degenRegex.matcher(seq);
			if(degenMatcher.find()) {
				ArrayList<String> expandedSeq = expandDegenerated(seq, 0, new ArrayList<String>());
				
				// Remove the original entry which contained degenerate bases, replace with all the possible sequences
				primerDict.remove(key);
				for(int i = 0; i < expandedSeq.size(); i++) {
					String newID = id + "_" + Integer.toString(i);
					String newSeq = expandedSeq.get(i);
					primerDict.put(newID, newSeq);
				}
			}
		}
		
		// Must now write a primer file containing no degenerate bases for the BLAST
		File cleanedPrimers = new File(outDir.getAbsolutePath() + sep + "primer_tmp.fasta");
		try{
			FileWriter writer = new FileWriter(cleanedPrimers);
			for(String key : primerDict.keySet()) {
				writer.write(">" + key + "\n");
				writer.write(primerDict.get(key));
				writer.write("\n");
			}
			writer.close();
		}catch(IOException e) {
			e.printStackTrace();
		}		
	}
	
	// Expand the sequences that contain degenerate bases into every possibility
	public static ArrayList<String> expandDegenerated(String seq, int index, ArrayList<String> primerContainer){		
		
		char[] seqChars = seq.toCharArray();
		// Due to recursive nature, need to keep going from where we left off
		for(int i = index; i < seq.length(); i++) {
			char c = seqChars[i];
			
			// Check if the current character is contained in the list of degenerate bases. If so, replace this instance with each possible base.
			if(degenerates.keySet().contains(c)) {
				for(char s : degenerates.get(c)) {
					String newSeq = seq.replaceFirst(Character.toString(c), Character.toString(s));
					Matcher matcher = degenRegex.matcher(newSeq);
					
					// Check the resulting primers.
					// If more degenerate bases are found, do the same as above, but starting from the base following the one just replaced.
					if(matcher.find()) {
						int j = i + 1;
						ArrayList<String> expanded = expandDegenerated(newSeq, j, primerContainer);
						
					// If no more degenerate bases are found, we have reached the end, and can add the sequence to the list to be returned.
					}else {
						primerContainer.add(newSeq);
					}
				}
			}
		}
		return primerContainer;
	}
	
	// Make a BLAST database from the primers
	public static void makeBlastDB(File reference, File BLASTLocation) {
		String in = reference.getAbsolutePath();
		String options = " -dbtype nucl -parse_seqids -hash_index -in " + in;
		String fullProcessCall = "makeblastdb.exe" + options;
		try{
			Process p = Runtime.getRuntime().exec(BLASTLocation.getAbsolutePath() + "\\" + fullProcessCall);
			try {
				p.waitFor();
			}catch(InterruptedException e) {
				e.printStackTrace();
			}
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	// Add the correct result headers to the Blast tsv output file
	public static void addHeaderToTSV(File tsvFile) {
		String tab = "\t";
		String[] headerFileIDs = new String[] {"qseqid", "sseqid", "positive", "mismatch", "gaps", "evalue",
				"bitscore", "slen", "length", "qstart", "qend", "qseq", "sstart", "send", "sseq"};
		String header = String.join(tab, headerFileIDs);
		
		ArrayList<String> lines = new ArrayList<String>();
		String line;
		try{
			BufferedReader reader = new BufferedReader(new FileReader(tsvFile));
			while((line = reader.readLine()) != null) {
				lines.add(line);
			}
			reader.close();
		}catch(IOException e) {
			e.printStackTrace();
		}
		
		lines.add(0, header);
		try{
			File newFile = new File(tsvFile.getAbsolutePath());
			tsvFile.delete();
			FileWriter writer = new FileWriter(newFile);
			for(String item : lines) {
				writer.write(item + System.getProperty("line.separator"));
			}
			writer.close();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	// Fills the sampleDict to be used in the consolidated report method
	public static void parseBlastOutput(File consolidatedDir, File detailedReport, HashMap<String, String> primerDict,
			int mismatches, HashMap<String, Sample> sampleDict) {
		
		// List of blast report files
		ArrayList<File> reportList = new ArrayList<File>();
		for(File sampleDir : detailedReport.listFiles()) {
			for(File sample : sampleDir.listFiles()) {
				if(sample.getName().contains(".tsv")) {
					reportList.add(sample);
				}
			}
		}
		
		for(File sampleReport : reportList) {
			String sampleName = sampleReport.getName().split("\\.tsv")[0];
			
			String line;
			try {
				BufferedReader reader = new BufferedReader(new FileReader(sampleReport));
				while((line = reader.readLine()) != null) {
					if(line.equals("") || line.startsWith("qseqid")) {
						continue;
					}
					String[] fields = line.split("\t");
					String qseqid = fields[0];
					String sseqid = fields[1];
					int length = Integer.parseInt(fields[8]);
					int weightedLength = primerDict.get(sseqid).length();
					int actualMismatches = Integer.parseInt(fields[3]);
					
					if(length == weightedLength && actualMismatches <= mismatches) {
						
						int qstart = Integer.parseInt(fields[9]);
						int qend = Integer.parseInt(fields[10]);
						String sseq = fields[14];
						if(sampleDict.get(sampleName).getBlastResults().containsKey(sseqid)) {
							sampleDict.get(sampleName).addBlastResult(sseqid, new BlastResult(sampleName, qseqid, sseqid, actualMismatches, qstart, qend, length, sseq));
						}else {
							sampleDict.get(sampleName).addNewBlastResult(sseqid, new BlastResult(sampleName, qseqid, sseqid, actualMismatches, qstart, qend, length, sseq));
						}
						
					}
				}
				reader.close();
			}catch(IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Makes the final consolidated report from the multiple blast reports
	public static void makeConsolidatedReport(File consolidatedDir, String sep, HashMap<String, Sample> sampleDict,
			HashMap<String, String> primerDict) {
		
		// Check to see if this is a qPCR or a regular PCR for formatting purposes
		boolean qPCR = false;
		for(String key : primerDict.keySet()) {
			if(key.split("-")[key.split("-").length - 1].startsWith("P")) {
				qPCR = true;
				break;
			}
		}
		
		// The header for the consolidated report
		String header = "";
		if(qPCR) {
			header = String.join("\t", new String[] {"Sample", "Gene", "GenomeLocation", "AmpliconSize", "Contig", "ForwardPrimers", "ReversePrimers",
					"ForwardMismatches", "ReverseMismatches", "Probe", "ProbeLocation", "ProbeSize", "ProbeMismatches"}) + "\n";
		}else {
			header = String.join("\t", new String[] {"Sample", "Gene", "GenomeLocation", "AmpliconSize", "Contig", "ForwardPrimers", "ReversePrimers",
					"ForwardMismatches", "ReverseMismatches"}) + "\n";
		}
		
		// Generate the file to be filled in
		File consolidatedReport = new File(consolidatedDir.getAbsolutePath() + sep + "report.tsv");
		try{
			FileWriter writer = new FileWriter(consolidatedReport);
			writer.write(header);
			writer.write(System.getProperty("line.separator"));
			
			for(String key : sampleDict.keySet()) {
				
				// Set up all necessary values
				Sample sample = sampleDict.get(key);
				String sampleName = key;
				HashMap<String, ArrayList<BlastResult>> blastResults = sampleDict.get(key).getBlastResults();
				if(!blastResults.isEmpty()) {
					String[] primerHits = blastResults.keySet().toArray(new String[blastResults.keySet().size()]); // Similar to what we did with the degenRegex issue
					HashMap<String, HashMap<String, ArrayList<String>>> primers = new HashMap<String, HashMap<String, ArrayList<String>>>();
					
					/* What this is actually doing is placing the primers into a hashmap based on their base name, alongside
					 *A list of directions. Therefore, a primer set of NAME-F and NAME-R would be listed under NAME with
					 *Directions F and R. Similarly, a degenerate primer of NAME-F_1, NAME-F_2, NAME-R_1, and NAME-R_2 would be
					 *listed under NAME with directions F_1, F_2, R_1, and R_2
					 */
					for(String primer : primerHits) {
						String[] splitPrimer = primer.split("-"); 
						String direction = splitPrimer[splitPrimer.length - 1];
						String primerName = String.join("-", Arrays.copyOfRange(splitPrimer, 0, splitPrimer.length - 1));
						if(!primers.containsKey(primerName)) {
							HashMap<String, ArrayList<String>> list = new HashMap<String, ArrayList<String>>();
							ArrayList<String> fList = new ArrayList<String>();
							ArrayList<String> rList = new ArrayList<String>();
							ArrayList<String> pList = new ArrayList<String>();
							if(direction.startsWith("F")) {
								fList.add(direction);
							}else if(direction.startsWith("R")) {
								rList.add(direction);
							}else if(direction.startsWith("P")) {
								pList.add(direction);
							}
							list.put("F", fList);
							list.put("R", rList);
							list.put("P", pList);
							primers.put(primerName, list);
						}else {
							if(direction.startsWith("F")) {
								primers.get(primerName).get("F").add(direction);
							}else if(direction.startsWith("R")) {
								primers.get(primerName).get("R").add(direction);
							}else if(direction.startsWith("P")) {
								primers.get(primerName).get("P").add(direction);
							}
						}
					}					
					
					// Check if primer pairs are present
					for(String primerKey : primers.keySet()) {
						HashMap<String, ArrayList<String>> primersList = primers.get(primerKey);
						if(!primersList.get("F").isEmpty() && !primersList.get("R").isEmpty()) { // Have both F and R primers
							String gene = primerKey;
							for(String fPrimer : primersList.get("F")) {
								for(String rPrimer : primersList.get("R")) {
									String fwdPrimer = gene + "-" + fPrimer;
									String revPrimer = gene + "-" + rPrimer;
									
									for(BlastResult fResult : sample.getBlastResults().get(fwdPrimer)) {
										for(BlastResult rResult : sample.getBlastResults().get(revPrimer)) {
											
											// If this pair of primers are not on the same contig, skip and keep going
											if(!fResult.getQueryID().equals(rResult.getQueryID())) {
												continue;
											}
											int startF = fResult.getStart();
											int endF = fResult.getEnd();
											int startR = rResult.getStart();
											int endR = rResult.getEnd();
											Integer[] positions = {startF, endF, startR, endR};
											int start = Collections.min(Arrays.asList(positions));
											int end = Collections.max(Arrays.asList(positions));
											String location = Integer.toString(start) + "-" + Integer.toString(end);
											String size = Integer.toString(end - start + 1);
											String contig = fResult.getQueryID();
											String fwdMismatch = Integer.toString(fResult.getMismatch());
											String revMismatch = Integer.toString(rResult.getMismatch());
											
											// If a qPCR probe exists
											if(qPCR) {
												if(!primersList.get("P").isEmpty()) {
													for(String pPrimer : primersList.get("P")) {
														String probePrimer = gene + "-" + pPrimer;
														for(BlastResult pResult : sample.getBlastResults().get(probePrimer)) {
															int startP = pResult.getStart();
															int endP = pResult.getEnd();
															String locationP = Integer.toString(startP) + "-" + Integer.toString(endP);
															String sizeP = Integer.toString(endP - startP + 1);
															String pMismatch = Integer.toString(pResult.getMismatch());
															
															// Probe only valid if it is contained within the surrounding amplicon
															if(startP >= start && endP <= end) {
																writer.write(String.join("\t", new String[] {sampleName, gene, location, size, contig,
																		fwdPrimer, revPrimer, fwdMismatch, revMismatch, probePrimer, locationP,
																		sizeP, pMismatch}));
																writer.write(System.getProperty("line.separator"));
															}
														}
													}
												}
											}else {
												writer.write(String.join("\t", new String[] {sampleName, gene, location, size, contig, 
														fwdPrimer, revPrimer, fwdMismatch, revMismatch}));
												writer.write(System.getProperty("line.separator"));
											}
										}
									}
								}
							}
						}
					}
				}
			}
			writer.close();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void makeQALog(File qLog, String version, File outputDir, File inputFile, File primerFile) {
		try(FileWriter writer = new FileWriter(qLog)) {
			String sep = System.getProperty("line.separator");
			writer.write("In Silico PCR version: " + version);
			writer.write(sep);
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			Date date = new Date();
			writer.write("Date run: " + dateFormat.format(date));
			writer.write(sep);
			writer.write("Run by user: " + System.getProperty("user.name"));
			writer.write(sep);
			writer.write("Output Folder: " + outputDir.getAbsolutePath());
			writer.write(sep);
			writer.write("Primer File: " + primerFile.getAbsolutePath());
			writer.write(sep);
			writer.write("Input File(s) :");
			if(inputFile.isDirectory()) {
				for(File file : inputFile.listFiles()) {
					writer.write(sep);
					writer.write(file.getAbsolutePath());
				}
			}else {
				writer.write(sep);
				writer.write(inputFile.getAbsolutePath());
			}
			writer.close();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public static boolean checkVersion() {
		try{
			URL url = new URL("https://github.com/chmaraj/In_Silico_PCR/releases");
			try{
				URLConnection connection = url.openConnection();
				InputStream in = connection.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(in));
				String line = "";
				int latestVersion = 0;
				while((line = reader.readLine()) != null) {
					if(line.contains("<a href=\"/chmaraj/In_silico_PCR/releases/tag")){
						if(line.contains("</a>")) {
							String version_name = line.split(">")[1];
							version_name = version_name.split("<")[0];
							version_name = version_name.split(" ")[version_name.split(" ").length - 1];
							version_name = version_name.substring(1, version_name.length());
							String[] splitVersion = version_name.split("\\.");
							version_name = String.join("", splitVersion);
							if(Integer.parseInt(version_name) > latestVersion) {
								latestVersion = Integer.parseInt(version_name);
							}
						}
					}
				}
				String currentVersion = String.join("", MainRun.version.substring(1, MainRun.version.length()).split("\\."));
				if(Integer.parseInt(currentVersion) == latestVersion) {
					return true;
				}
			}catch(IOException e) {
				e.printStackTrace();
			}
		}catch(MalformedURLException e) {
			e.printStackTrace();
		}
		return false;
	}

	// Make a synthetic gel image of what the PCR would look like
	public static void makeSyntheticGel(Scene scene, HashMap<String, Sample> sampleDict, File consolidatedReport) {
		Canvas canvas = new Canvas();
		canvas.setWidth(scene.getWidth());
		canvas.setHeight(scene.getHeight());
		GraphicsContext gc = canvas.getGraphicsContext2D();
		
		gc.setStroke(Color.WHITE);
		gc.setFill(Color.BLACK);
		double laneWidth = canvas.getWidth() / 22;
		double laneHeight = canvas.getHeight() * 8 / 10;
		double horizontalInset = canvas.getWidth() * 1 / 20;
		double verticalInset = (canvas.getHeight() * 2 / 10) - 20;
		double bandWidth = 5;
		for(int i = 0; i < 20; i++) {
			gc.fillRect((i * laneWidth) + horizontalInset, verticalInset, laneWidth, laneHeight);
			gc.strokeRect((i * laneWidth) + horizontalInset, verticalInset, laneWidth, laneHeight);
		}
		
		double controlInset = verticalInset + (laneHeight * 2 / 10);
		double controlHeight = laneHeight * 6 / 10;
		
		drawLadder(gc, horizontalInset, controlInset, controlHeight, laneWidth, bandWidth);
		
		gc.save();
		gc.rotate(-90);
		gc.setFont(new Font("Verdana", 10));
		gc.strokeText("Ladder", -verticalInset + 3, horizontalInset + (laneWidth / 2) + 5);
	}
	
	public static void drawLadder(GraphicsContext gc, double horizontalInset, double controlInset, double controlHeight, double laneWidth, double bandWidth) {
		// Set out the max and min band heights, corresponding to 20kb and 100bp, respectively. 
				gc.setFill(Color.WHITE);
				gc.fillRect(horizontalInset, controlInset, laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + controlHeight, laneWidth, bandWidth);
				
				// Normalize control band values so they make sense
				double band10kb = (Math.log10(10000) - 2) / 2.3;
				double band7kb = (Math.log10(7000) - 2) / 2.3;
				double band5kb = (Math.log10(5000) - 2) / 2.3;
				double band4kb = (Math.log10(4000) - 2) / 2.3;
				double band3kb = (Math.log10(3000) - 2) / 2.3;
				double band2kb = (Math.log10(2000) - 2) / 2.3;
				double band1_5kb = (Math.log10(1500) - 2) / 2.3;
				double band1kb = (Math.log10(1000) - 2) / 2.3;
				double band700 = (Math.log10(700) - 2) / 2.3;
				double band500 = (Math.log10(500) - 2) / 2.3;
				double band400 = (Math.log10(400) - 2) / 2.3;
				double band300 = (Math.log10(300) - 2) / 2.3;
				double band200 = (Math.log10(200) - 2) / 2.3;
				
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band10kb)), laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band7kb)), laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band5kb)), laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band4kb)), laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band3kb)), laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band2kb)), laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band1_5kb)), laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band1kb)), laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band700)), laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band500)), laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band400)), laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band300)), laneWidth, bandWidth);
				gc.fillRect(horizontalInset, controlInset + (controlHeight - (controlHeight * band200)), laneWidth, bandWidth);
				
				gc.setStroke(Color.BLACK);
				gc.strokeText("20kb", 5, controlInset + 5);
				gc.strokeText("10kb", 5, controlInset + (controlHeight - (controlHeight * band10kb)) + 5);
				gc.strokeText("7kb", 5, controlInset + (controlHeight - (controlHeight * band7kb)) + 5);
				gc.strokeText("5kb", 5, controlInset + (controlHeight - (controlHeight * band5kb)) + 5);
				gc.strokeText("4kb", 5, controlInset + (controlHeight - (controlHeight * band4kb)) + 5);
				gc.strokeText("3kb", 5, controlInset + (controlHeight - (controlHeight * band3kb)) + 5);
				gc.strokeText("2kb", 5, controlInset + (controlHeight - (controlHeight * band2kb)) + 5);
				gc.strokeText("1.5kb", 5, controlInset + (controlHeight - (controlHeight * band1_5kb)) + 5);
				gc.strokeText("1kb", 5, controlInset + (controlHeight - (controlHeight * band1kb)) + 5);
				gc.strokeText("700", 5, controlInset + (controlHeight - (controlHeight * band700)) + 5);
				gc.strokeText("500", 5, controlInset + (controlHeight - (controlHeight * band500)) + 5);
				gc.strokeText("400", 5, controlInset + (controlHeight - (controlHeight * band400)) + 5);
				gc.strokeText("300", 5, controlInset + (controlHeight - (controlHeight * band300)) + 5);
				gc.strokeText("200", 5, controlInset + (controlHeight - (controlHeight * band200)) + 5);
				gc.strokeText("100", 5, controlInset + controlHeight + 5);
	}
	
	// Simple method to print a message to the output TextArea
	public static void logMessage(TextArea outputField, String msg) {
		Platform.runLater(() -> outputField.appendText("\n" + msg + "\n"));;
	}
		
}

