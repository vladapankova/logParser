import javax.sound.sampled.SourceDataLine;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;


public class Solution2 {
    public static void main(String[] args)  {
        String inputFilePath = "input.txt";
        try {
            String inputFile = Path.of(inputFilePath).toAbsolutePath().toString();
            LogParser logParser = new LogParser(inputFile);
            logParser.Parse();
        } catch (InvalidInputTxtException e) {
            System.out.println("Ошибка, связанная с файлом " + inputFilePath);
            System.out.println(e.getMessage());
        } catch (Exception e) {
            System.out.println("Произошла критическая ошибка. Выполнение программы остановлено. Подробности: ");
            e.printStackTrace();
        }
    }
}