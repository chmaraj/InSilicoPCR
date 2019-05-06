package insilicopcr;

import java.util.ArrayList;
import java.util.HashMap;

public class Sample {
	
	private ArrayList<String> sampleFiles = new ArrayList<String>();
	private String name;
	private String fileType;
	private HashMap<String, ArrayList<BlastResult>> blastResults = new HashMap<String, ArrayList<BlastResult>>();
	private String assemblyFile;

	public Sample() {
		
	}
	
	public String getName() {
		return this.name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public ArrayList<String> getFiles() {
		return this.sampleFiles;
	}
	
	public void setFile(String file) {
		sampleFiles = new ArrayList<String>();
		sampleFiles.add(file);
	}
	
	public void setFiles(ArrayList<String> files) {
		this.sampleFiles = files;
	}
	
	public void addFile(String fileName) {
		this.sampleFiles.add(fileName);
	}
	
	public String getFileType() {
		return this.fileType;
	}
	
	public void setFileType(String fileType) {
		this.fileType = fileType;
	}
	
	public HashMap<String, ArrayList<BlastResult>> getBlastResults(){
		return this.blastResults;
	}
	
	public void addBlastResult(String key, BlastResult results) {
		this.blastResults.get(key).add(results);
	}
	
	public void addNewBlastResult(String key, BlastResult results) {
		ArrayList<BlastResult> temp = new ArrayList<BlastResult>();
		temp.add(results);
		this.blastResults.put(key, temp);
	}
	
	public String getAssemblyFile() {
		return this.assemblyFile;
	}
	
	public void setAssemblyFile(String assemblyFile) {
		this.assemblyFile = assemblyFile;
	}
}
