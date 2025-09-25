package io.github.vdaburon.converthtml2pdf;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.logging.Logger;

import java.util.Properties;

import com.openhtmltopdf.java2d.api.Java2DRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.w3c.tidy.Tidy;

/**
 * Convert an HTML Page to PDF Document
 * Use Tidy to convert an HTML page to a well formated XHTML Page then use openhtmltopdf library to generate a PDF document
 */
public class Html2PDFConvertor {

    // CLI parameters OPT
    /** HTML file path to read **/
    public static final String K_FILE_HTML_IN_OPT = "html_in";
    /** PDF file path result **/
    public static final String K_FILE_PDF_OUT_OPT = "pdf_out";
    /** the width in pixels of images in the html page **/
    public static final String K_IMAGE_WIDTH_OPT  = "image_width";
    /** default image width pixels **/
    public static final int K_IMAGE_WIDTH_DEFAULT   = 960;

    private static final Logger LOGGER = Logger.getLogger(Html2PDFConvertor.class.getName());

    /**
     * main function
     * @param args Command Line Parameters
     */
    public static void main(String[] args) {
        String htmlFilePath = "NOT SET";
        String pdfFilePath = "NOT SET";
        int imageWidth = K_IMAGE_WIDTH_DEFAULT;

        long lStart = System.currentTimeMillis();
        LOGGER.info("Start main");

        Options options = createOptions();
        Properties parseProperties = null;

        try {
            parseProperties = parseOption(options, args);
        } catch (ParseException ex) {
            helpUsage(options);
            LOGGER.info("main end (exit 1) ERROR");
            System.exit(1);
        }

        String sTmp = "";
        sTmp = (String) parseProperties.get(K_FILE_HTML_IN_OPT);
        if (sTmp != null) {
            htmlFilePath = sTmp.replace('\\','/');

        }


        sTmp = (String) parseProperties.get(K_FILE_PDF_OUT_OPT);
        if (sTmp != null) {
            pdfFilePath = sTmp;
        }

        sTmp = (String) parseProperties.get(K_IMAGE_WIDTH_OPT);
        if (sTmp != null) {
            try {
                imageWidth = Integer.parseInt(sTmp);
            } catch (Exception ex) {
                LOGGER.warning("Error parsing integer parameter " + K_IMAGE_WIDTH_OPT + ", value = " + sTmp + ", set to " + K_IMAGE_WIDTH_DEFAULT + " (default)");
                imageWidth = K_IMAGE_WIDTH_DEFAULT;
            }
        }

        convertHtmlToPdf(htmlFilePath, pdfFilePath, imageWidth);

        long lEnd = System.currentTimeMillis();
        long lDurationMs = lEnd - lStart;
        LOGGER.info("Duration ms : " + lDurationMs);
        LOGGER.info("End main OK exit(0)");
        System.exit(0);
    }

    /**
     * Convert an HTML page to an PDF document
     * @param htmlFilePath html page to read
     * @param pdfFilePath pdf file name to write
     * @param imageWidth image width in the html page to set the PDF width format
     */
    private static void convertHtmlToPdf(String htmlFilePath, String pdfFilePath, int imageWidth) {

        String xhtmlFiletempoPath = htmlFilePath + ".xhtml.tmp";

        InputStream fileis = null;
        BufferedReader brHtml = null;
        try {
            fileis = new FileInputStream(htmlFilePath);
            InputStreamReader insr = new InputStreamReader(fileis);
            brHtml = new BufferedReader(insr);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        // Convert a html file to a temporary xhtml file and correct bad html elements with Tidy Library
        BufferedOutputStream outXhtmlFile = null;
        try {
            outXhtmlFile = new BufferedOutputStream(new FileOutputStream(xhtmlFiletempoPath));
            LOGGER.info("Before Call Tidy Library");
            Tidy tidy = new Tidy(); // obtain a new Tidy instance
            tidy.setXHTML(true); // set desired config options using tidy setters

            tidy.parse(brHtml, outXhtmlFile); // run tidy, convert a html file in a correct xhtml file
            LOGGER.info("After Call Tidy Library, temporary file created="+ xhtmlFiletempoPath);
            brHtml.close();
            outXhtmlFile.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        try {
            LOGGER.info("Before openhtmltopdf call");
            LOGGER.info("Convert xhtml file=" + xhtmlFiletempoPath + " to pdf file="+ pdfFilePath);

            File fileTempo = new File(xhtmlFiletempoPath);
            // convert the temporary xhtml file to a PDF file with openhtmltopdf library
            OutputStream os = new FileOutputStream(pdfFilePath);
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            // Compute the width in mm of the pdf correlated to image pixels width to avoid truncated images in the PDF
            // 960px to display in A4 format = 210mm * 1.4 = 294 mm = 294/960 = 0.3 (empirical calculation)
            // 1024px * 0.3 = 307 mm
            long pixWidth = imageWidth;
            long mmWidth = Math.round(pixWidth * 0.3);
            builder.useDefaultPageSize((int) mmWidth, (int) (297 * 1.2), Java2DRendererBuilder.PageSizeUnits.MM);
            builder.withFile(fileTempo);
            builder.toStream(os);
            builder.run();
            LOGGER.info("After openhtmltopdf call");

            if (fileTempo.exists() && fileTempo.canWrite()) {
                LOGGER.info("Delete temporary file="+ xhtmlFiletempoPath);
                fileTempo.delete();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Create the Command Line Parameters Options
     *
     * @return Option CLI
     */
    private static Options createOptions() {
        Options options = new Options();

        Option helpOpt = Option.builder("help").hasArg(false).desc("Help and show parameters").build();
        options.addOption(helpOpt);

        Option htmlInOpt = Option.builder(K_FILE_HTML_IN_OPT).argName(K_FILE_HTML_IN_OPT).hasArg(true)
                .required(true).desc("Html file to read (e.g: index.html)").build();
        options.addOption(htmlInOpt);

        Option pdfOutOpt = Option.builder(K_FILE_PDF_OUT_OPT).argName(K_FILE_PDF_OUT_OPT).hasArg(true)
                .required(true).desc("PDF file generated (e.g: report.pdf)").build();
        options.addOption(pdfOutOpt);

        Option imageWidthOpt = Option.builder(K_IMAGE_WIDTH_OPT).argName(K_IMAGE_WIDTH_OPT).hasArg(true)
                .required(false)
                .desc("Optional, Image width in pixels referenced by the html page (e.g:960)")
                .build();
        options.addOption(imageWidthOpt);

        return options;
    }

    /**
     * Convert the main args parameters to properties
     *
     * @param optionsP the command line options declared
     * @param args     the cli parameters
     * @return properties
     * @throws ParseException         can't parse command line parmeter
     * @throws MissingOptionException a parameter is mandatory but not present
     */
    private static Properties parseOption(Options optionsP, String[] args)
            throws ParseException, MissingOptionException {
        Properties properties = new Properties();

        CommandLineParser parser = new DefaultParser();
        // parse the command line arguments
        CommandLine line = parser.parse(optionsP, args);

        if (line.hasOption("help")) {
            properties.setProperty("help", "help value");
            return properties;
        }

        if (line.hasOption(K_FILE_HTML_IN_OPT)) {
            properties.setProperty(K_FILE_HTML_IN_OPT, line.getOptionValue(K_FILE_HTML_IN_OPT));
        }

        if (line.hasOption(K_FILE_PDF_OUT_OPT)) {
            properties.setProperty(K_FILE_PDF_OUT_OPT, line.getOptionValue(K_FILE_PDF_OUT_OPT));
        }

        if (line.hasOption(K_IMAGE_WIDTH_OPT)) {
            properties.setProperty(K_IMAGE_WIDTH_OPT, line.getOptionValue(K_IMAGE_WIDTH_OPT));
        }
        return properties;
    }

    /**
     * Help to command line parameters
     *
     * @param options the command line options declared
     */
    private static void helpUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        String footer = "E.g : java -jar convert-html-to-pdf-<version>-jar-with-dependencies.jar -" + K_FILE_HTML_IN_OPT + " index.html -" + K_FILE_PDF_OUT_OPT + " report.pdf -"
                + K_IMAGE_WIDTH_OPT + " 960\n";

        formatter.printHelp(120, Html2PDFConvertor.class.getName(),
                Html2PDFConvertor.class.getName(), options, footer, true);
    }
}