import javax.sound.sampled.SourceDataLine;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;


public class Solution2 {
    private static String logPath;
    private static String neededText;
    public static String line = "";
    public static String temp = "";
    public static String previousLine = "";
    public static String UID = "";
    public static String test = "";
    public static int indexFrom = 0;
    public static int indexTo = 0;


    public static HashMap<String, String> UIDMap = new HashMap<>();


    public static void main(String[] args) throws Exception {
        try {
            Scanner scanner = new Scanner(Path.of("C:\\Temp\\input.txt"));

            String temp = scanner.nextLine();
            if (temp.startsWith("!")) {
                setLogPath(temp.substring(1));
            }
            temp = scanner.nextLine();
            if (temp.startsWith("?")) {
                setNeededText(temp.substring(1));
            }
        } catch (IOException e) {
            System.out.println("Возникла ошибка: " + e);
            e.printStackTrace();
        }

        try (InputStream input = Files.newInputStream(Path.of(logPath));
             InputStreamReader reader = new InputStreamReader(input);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            String currentDateTime = formatter.format(calendar.getTime());
            String path = "C:\\Temp\\" + currentDateTime;
            Files.createDirectory(Path.of(path));//создали папку текущего запроса
            String logPath = "";

            while (bufferedReader.ready()) {
                temp = bufferedReader.readLine();
                if (!temp.contains("UID=")) {
                    line = line + "\n" + temp;

                } else line = temp;
                if (line.contains(neededText)) {
                    UID = UIDsearcher(line);
                    logPath = path + "\\UID" + UID + ".txt";
                    UIDMap.put(UID, logPath);   //добавили UID в множество
                }

            }

        } catch (Exception e) {
            System.out.println("Возникла ошибка: " + e);
            e.printStackTrace();
        }
        try (InputStream input = Files.newInputStream(Path.of(logPath));
             InputStreamReader reader = new InputStreamReader(input);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            while (bufferedReader.ready()) {
                previousLine = temp;
                temp = bufferedReader.readLine();

                if (!temp.contains("UID=")) {
                    line = line + "\n" + temp;
                } else line = temp;


                for (String UIDNumber : UIDMap.keySet()) {  //пробегаем по множеству UID
                    if (line.contains(UIDNumber)) {
                        logPath = UIDMap.get(UIDNumber);
                        Path file = Path.of(logPath);
                        try (FileWriter writer = new FileWriter(logPath, true)) {
                            if (Files.notExists(file)) {
                                Files.createFile(file); //создали файлик для данного UID
                            }
                            writer.write(temp + "\n");//записываем строку в свой UID
                        }
                    }
                }
            }


        } catch (
                Exception e) {
            System.out.println("Возникла ошибка: " + e);
            e.printStackTrace();
        }

    }

    public static String UIDsearcher(String line) {
        indexFrom = line.indexOf("UID=");
        indexTo = line.indexOf(" ", indexFrom);
        UID = line.substring(indexFrom + 4, indexTo);   //нашли UID
        return UID;
    }

    public static void setLogPath(String logPathSet) {
        logPath = logPathSet;
    }

    public static void setNeededText(String neededTextSet) {
        neededText = neededTextSet;
    }
}