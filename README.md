# In Silico PCR

The *In Silico* PCR tool is intended as a means of identifying primer binding and possible PCR products in an electronic setting. It is, of course, best used as a screening tool, and not meant as evidence or a guarantee. The products that are seen as output from this tool are hypothetical, and do not account for primer binding affinity or other metrics of PCR success. It is expected that the user will take the results from this tool and further verify the PCR qualities through sites like Primer3.

In it's current state, the program can accept both fasta and fastq files within the same input directory if the user desires. However, you cannot currently input both fasta and fastq files for the same sample name. i.e. if you have a sample named SampleX, you can have one or two SampleX.fastq files (single or paired), and you can have a SampleX_assembly.fasta file, but you cannot have both SampleX.fastq and SampleX.fasta files. 

# How it works

The tool works through one main pipeline, with an offshoot in the event of fastq input. FastQ input will cause the program to take significantly longer to complete. The program works as follows:

1. Set up objects required to house and format primer and sample information. 
2. If fastq files are used, bait the reads with primer sequences using BBDuk from BBTools suite. 
    - Bait the reads using the recovered reads from step 2. 
    - Assemble retrieved reads to increase the odds of finding valid PCR products in slightly longer contigs. 
3. Create BLAST database from the primers. 
4. Run BLAST on primer database using either assembled reads (if fastq) or fasta sequences (if fasta). 
5. Parse output into a consolidated report that only reports valid PCR products. 

# Dependencies

This is a Java-based GUI for ease-of-use, and is packaged with the following dependencies:
- BBTools (https://jgi.doe.gov/data-and-tools/bbtools/)
- BLAST+ (https://blast.ncbi.nlm.nih.gov/Blast.cgi?CMD=Web&PAGE_TYPE=BlastDocs&DOC_TYPE=Download)
- Apache Commons CLI (https://commons.apache.org/proper/commons-cli/)

# Downloading and Running

Downloading and running the program is simple. Go to the releases tab in this repository and select the most recent release of the program. This release will contain two main resources: a .zip file and a .jar file. 

The .zip file contains both the most recent .jar file, along with all the dependencies required to run the program. Download this file and extract it to a directory. The files contained within **MUST** be kept together, or else the program will not run correctly. 

To run the program, ensure all the files from the .zip file are in the same directory, then simply double-click the .jar file.

### Running on cmd/terminal

As of version 0.310, the program is capable of being run through command line/terminal. Note that in the usage examples, the working directory is the **bin** directory of the release. For Windows, proper usage is as follows:

```

java -cp commons-cli-1.4.jar;. commandpcr.Entry -i input(s) -o outputdir -p primerfile [t number] [-m number]

```

And proper usage on Linux systems is as follows:

```

java -cp commons-cli-1.4.jar:. commandpcr.Entry -i input(s) -o outputdir -p primerfile [t number] [-m number]

```

The options are explained here:

```

-i,--input <arg>        The input file/directory containing the .fasta or
                        .fastq sequence(s)
-m,--mismatches <arg>   The number of mismatches permitted. Default is 0.
-o,--output <arg>       The directory to contain the output
-p,--primers <arg>      The custom primer file containing the putative
                        PCR primers
-t,--threads <arg>      The number of threads to use. Default is maximum
                        number of processors available.

```

# Updating

If the version of the program you are running is not the most up-to-date version, the interface will display an alert text notifying you of the option of updating. If you only use the GUI, you can simply go to the releases section of this repository and download the .jar file associated with the most recent release, not the .zip file. Place this .jar file in the directory containing the old program and its dependencies, then just double-click the new version. 

# Known Bugs/Issues

- Calling the program in terminal from a directory that is not in the direct path to the In Silico PCR program will cause a crash. For instance, if the program is contained in the home directory, calling the java program while in a separate media directory will cause the program to crash. This appears to be a problem with the 'user.dir' system property used to identify the location of the program dependencies.
