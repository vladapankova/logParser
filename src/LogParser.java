import com.sun.nio.sctp.AbstractNotificationHandler;
import org.w3c.dom.ls.LSOutput;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.cert.Extension;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

//import org.apache.*;
public class LogParser {

    private ArrayList<String> inputLogPathList = new ArrayList<>();
    private ArrayList<String> neededTextList = new ArrayList<>();
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

    private void ReadInputTxtAndSetParams() throws InvalidInputTxtException {
        if (Files.notExists(Path.of(inputFilePath))) {
            throw new InvalidInputTxtException("Не существует файла " + inputFilePath + ", из которого я узнаю, что вы ищете.");
        }
        try (Scanner scanner = new Scanner(Path.of(inputFilePath))) {
            if (!scanner.hasNext()) {
                throw new InvalidInputTxtException("Файл " + inputFilePath + " пустой. Задайте путь через ! и искомый текст через ?");
            }
            while (scanner.hasNext()) {
                String temp = scanner.nextLine();
                if (temp.startsWith("!") && temp.length() > 1) {
                    var inputLogPath = temp.substring(1);
                    inputLogPathList.add(inputLogPath);
                }
                if (temp.startsWith("?") && temp.length() > 1) {
                    var neededText = temp.substring(1);
                    neededTextList.add(neededText);
                }
            }
        } catch (IOException e) {
            throw new InvalidInputTxtException("Ошибка чтения файла " + inputFilePath, e);
        }
        if (neededTextList.isEmpty()) {
            throw new InvalidInputTxtException("Не указан хотя бы один искомый текст.");
        }
        if (inputLogPathList.isEmpty()) {
            throw new InvalidInputTxtException("Не указан хотя бы один путь поиска.");
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

    private String UIDsearcher(String line) throws IOException {
        if (line.length() < UID_MARKER.length()) {
            throw new IOException("В строке не может быть " + UID_MARKER);
        }
        int indexFrom;
        int indexTo;

        indexFrom = line.indexOf(UID_MARKER);
        indexTo = line.indexOf(" ", indexFrom + UID_MARKER.length());
        var UID = line.substring(indexFrom + UID_MARKER.length(), indexTo);   //нашли UID
        return UID;

    }

    private void SearchNeededUIDs() throws InvalidInputTxtException, IOException {
        if (inputLogPathList.isEmpty() || neededTextList.isEmpty()) {   //проверяем, что есть хоть один путь и хоть один искомый текст
            throw new InvalidInputTxtException("Не заданы параметры для работы программы");
        }
        for (String inputLogPathElement : inputLogPathList) {    //проверяем, существуют ли файлы по всем искомым путям, где искать мы будем
            if (Files.notExists(Path.of(inputLogPathElement))) {
                throw new InvalidInputTxtException("Не существует файла " + inputLogPathElement + ", в котором я буду искать.");
            }
        }
        String resultDirectoryPath = CreateResultDirectory();
        for (String inputLogPathElement : inputLogPathList) {   //ищем в каждом заданном пути искомый текст
            SearchNeededTextInFile(inputLogPathElement, resultDirectoryPath);
        }
    }

    private void InnerWriteResultLogs(InputStream input) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(input);
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
                    var outputLogPath = UIDMap.get(UIDNumber);
                    Path file = Path.of(outputLogPath);
                    try (FileWriter writer = new FileWriter(outputLogPath, true)) {
                        if (Files.notExists(file)) {
                            Files.createFile(file); //создали файлик для данного UID
                        }
                        writer.write(temp + "\n");//записываем строку в свой UID
                    }
                }
            }
        }
    }

    private void WriteResultLogs() throws Exception {
        if (UIDMap.isEmpty()) { //если мы не нашли нужные UIDы
            System.out.println("Искомые " + UID_MARKER + " не найдены.");
            return;
        }
        for (String inputLogPathElement : inputLogPathList) {
            String extension = "";
            if (inputLogPathElement.contains(".")) {
                extension = inputLogPathElement.substring(inputLogPathElement.lastIndexOf(".") + 1);
            }
            if (extension.equalsIgnoreCase("ZIP")) {
                try (ZipFile zipFile = new ZipFile(inputLogPathElement)) {
                    final Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry zipEntry = entries.nextElement();
                        try (InputStream input = zipFile.getInputStream(zipFile.getEntry(zipEntry.getName()))) {
                            InnerWriteResultLogs(input);
                        } catch (IOException e) {
                            System.out.println("Произошла ошибка чтения архива" + zipEntry);
                            e.printStackTrace();
                            throw e;
                        }
                    }
                }
            } else {
                try (InputStream input = Files.newInputStream(Path.of(inputLogPathElement))) {
                    InnerWriteResultLogs(input);
                } catch (Exception e) {
                    System.out.println("Возникла ошибка чтения файла" + inputLogPathElement);
                    e.printStackTrace();
                    throw e;
                }
            }
        }
    }


    private void InnerSearchNeededTextInFile(InputStream input, String resultDirectoryPath) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(input);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line = "";
            while (bufferedReader.ready()) {
                var temp = bufferedReader.readLine();
                if (!temp.contains(UID_MARKER)) {   //если текущая строка не содержит UID==
                    line = line + "\n" + temp;  //записываем ее к предыдущим строчкам в line
                } else {
                    SearchAllNeededTextsInLog(line, resultDirectoryPath);
                    line = temp;
                }//если текущая строка содержит UID=, то line обновляется и эта строчка становится в ней первой
            }
            SearchAllNeededTextsInLog(line, resultDirectoryPath);   //необходимо выполнить проверку для последнего лога в файле
        }
    }

    private void SearchNeededTextInFile(String inputLogPathElement, String resultDirectoryPath) throws IOException {
        //проверка формата файла
        String extension = "";
        if (inputLogPathElement.contains(".")) {
            extension = inputLogPathElement.substring(inputLogPathElement.lastIndexOf(".") + 1);
        }
        if (extension.equalsIgnoreCase("ZIP")) {
            try (ZipFile zipFile = new ZipFile(inputLogPathElement)) {
                final Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry zipEntry = entries.nextElement();
                    try (InputStream input = zipFile.getInputStream(zipFile.getEntry(zipEntry.getName()))) {
                        InnerSearchNeededTextInFile(input, resultDirectoryPath);
                    } catch (IOException e) {
                        System.out.println("Произошла ошибка чтения архива: " + zipEntry);
                        e.printStackTrace();
                        throw e;
                    }
                }
            }
        } else try (InputStream input = Files.newInputStream(Path.of(inputLogPathElement))) {
            InnerSearchNeededTextInFile(input, resultDirectoryPath);
        } catch (IOException e) {
            System.out.println("Произошла ошибка чтения файла " + inputLogPathElement);
            e.printStackTrace();
            throw e;
        }

    }

    private void SearchAllNeededTextsInLog(String line, String resultDirectoryPath) throws IOException {
        if (line == null || line.length() == 0) {
            return;
        }//случай, когда в line хранится полный предыдущий лог
        ArrayList<String> tempCollection = new ArrayList<>();
        for (String neededTextElement : neededTextList) {
            if (line.contains(neededTextElement)) {   //если тек.строчка содержит этот искомый текст
                tempCollection.add(neededTextElement);  //добавляем во временную коллекцию этот текст, чтобы маркировать
            }
        }
        if (tempCollection.containsAll(neededTextList)) {
            var UID = UIDsearcher(line);
            var outputLogPath = resultDirectoryPath + "\\UID" + UID + ".txt";
            UIDMap.put(UID, outputLogPath);   //добавили UID в множество
        }
        tempCollection.clear();
    }
}

