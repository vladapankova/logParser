import javax.sound.sampled.SourceDataLine;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;


public class Solution2 {
    public static void main(String[] args) throws Exception {
        String inputFilePath = "C:\\Temp\\input.txt";
        LogParser logParser = new LogParser(inputFilePath);
        logParser.Parse();
    }
}