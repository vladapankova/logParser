import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Scanner;

public class LogParser {
    private String inputLogPath;
    private String neededText;
    private String inputFilePath;
    private HashMap<String, String> UIDMap = new HashMap<>();
    private final String UID_MARKER = "UID=";

    public LogParser(String inputFilePath) {
        this.inputFilePath = inputFilePath;
    }


    public void Parse() throws Exception {
        ReadInputTxtAndSetParams();

        SearchNeededUIDs();

        WriteResultLogs();

    }

    private void ReadInputTxtAndSetParams() throws IOException {
        // проверить считанные данные из файлика на валидность
        try {
            //          var test = Path.of("input.txt");
            Scanner scanner = new Scanner(Path.of(inputFilePath));

            String temp = scanner.nextLine();
            if (temp.startsWith("!")) {
                this.inputLogPath = temp.substring(1);
            }
            temp = scanner.nextLine();
            if (temp.startsWith("?")) {
                this.neededText = temp.substring(1);
            }
        } catch (IOException e) {
            System.out.println("Возникла ошибка: " + e);
            e.printStackTrace();
            throw e;
        }

    }

    private String CreateResultDirectory() throws IOException {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String currentDateTime = formatter.format(calendar.getTime());
        String directoryOfInputTxt = new File(inputFilePath).getParentFile().getAbsolutePath();
        String path = directoryOfInputTxt + "\\" + currentDateTime;
        Files.createDirectory(Path.of(path));//создали папку текущего запроса
        return path;
    }

    private String UIDsearcher(String line) {//сюда добавить проверку на то, что строка, с которой работаем, не меньше искомого текста
        int indexFrom;
        int indexTo;
        indexFrom = line.indexOf(UID_MARKER);
        indexTo = line.indexOf(" ", indexFrom + UID_MARKER.length());
        var UID = line.substring(indexFrom + UID_MARKER.length(), indexTo);   //нашли UID
        return UID;
    }

    private void SearchNeededUIDs() throws IOException {
        try (InputStream input = Files.newInputStream(Path.of(inputLogPath));
             InputStreamReader reader = new InputStreamReader(input);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            String resultDirectoryPath = CreateResultDirectory();
            String line = "";
            while (bufferedReader.ready()) {
                var temp = bufferedReader.readLine();
                if (!temp.contains(UID_MARKER)) {
                    line = line + "\n" + temp;

                } else line = temp;
                if (line.contains(neededText)) {
                    var UID = UIDsearcher(line);
                    var outputLogPath = resultDirectoryPath + "\\UID" + UID + ".txt";
                    UIDMap.put(UID, outputLogPath);   //добавили UID в множество
                }
            }

        } catch (Exception e) {
            System.out.println("Возникла ошибка: " + e);
            e.printStackTrace();
            throw e;
        }
    }

    private void WriteResultLogs() throws IOException {
        try (InputStream input = Files.newInputStream(Path.of(inputLogPath));
             InputStreamReader reader = new InputStreamReader(input);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line = "";
            while (bufferedReader.ready()) {
                var temp = bufferedReader.readLine();

                if (!temp.contains(UID_MARKER)) {
                    line = line + "\n" + temp;
                } else line = temp;


                for (String UIDNumber : UIDMap.keySet()) {  //пробегаем по множеству UID
                    if (!line.contains(UIDNumber))
                        continue;
                    //  if (line.contains(UIDNumber)) {
                    var outputLogPath = UIDMap.get(UIDNumber);
                    Path file = Path.of(outputLogPath);
                    try (FileWriter writer = new FileWriter(outputLogPath, true)) {
                        if (Files.notExists(file)) {
                            Files.createFile(file); //создали файлик для данного UID
                        }
                        writer.write(temp + "\n");//записываем строку в свой UID
                    }
                    // }
                }
            }


        } catch (
                Exception e) {
            System.out.println("Возникла ошибка: " + e);
            e.printStackTrace();
            throw e;
        }
    }
}
