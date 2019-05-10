# In Silico PCR

The *In Silico* PCR tool is intended as a means of identifying primer binding and possible PCR products in an electronic setting. It is, of course, best used as a screening tool, and not meant as evidence or a guarantee. The products that are seen as output from this tool are hypothetical, and do not account for primer binding affinity or other metrics of PCR success. It is expected that the user will take the results from this tool and further verify the PCR qualities through sites like Primer3.

In it's current state, the program can accept both fasta and fastq files within the same input directory if the user desires. However, you cannot currently input both fasta and fastq files for the same sample name. i.e. if you have a sample named SampleX, you can have one or two SampleX.fastq files (single or paired), and you can have a SampleX_assembly.fasta file, but you cannot have both SampleX.fastq and SampleX.fasta files. 

# Dependencies

This is a Java-based GUI for ease-of-use, and is packaged with the following dependencies:
- BBTools (https://jgi.doe.gov/data-and-tools/bbtools/)
- BLAST+ (https://blast.ncbi.nlm.nih.gov/Blast.cgi?CMD=Web&PAGE_TYPE=BlastDocs&DOC_TYPE=Download)
