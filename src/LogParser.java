import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

//import org.apache.*;
public class LogParser {
    private ArrayList<String> inputLogPathList = new ArrayList<>();
    private ArrayList<String> neededTextList = new ArrayList<>();
    private String timePeriod;
    Date dateFrom = new Date();
    Date dateTo = new Date();
    private String inputFilePath;
    private HashMap<String, String> UIDMap = new HashMap<>();
    private final String UID_MARKER = "UID=";

    public LogParser(String inputFilePath) {
        this.inputFilePath = inputFilePath;
    }

    public void Parse() throws Exception {

        ReadInputTxtAndSetParams();

        SearchAndWrite();

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
                    Path of = Path.of(inputLogPath);
                    if (Files.isDirectory(of)) {
                        try (Stream<Path> list = Files.walk(of)) {
                            var tempList = list.filter(Files::isRegularFile).toList();
                            if (tempList.isEmpty()) {
                                throw new InvalidInputTxtException("В папке " + inputLogPath + "нет файлов. Убедитесь, что в папке содержатся необходимые файлы.");
                            }
                            for (Path path : tempList) {
                                inputLogPathList.add(path.toString());
                            }
                        }
                    } else {
                        inputLogPathList.add(inputLogPath);
                    }
                }
                if (temp.startsWith("?") && temp.length() > 1) {
                    var neededText = temp.substring(1);
                    neededTextList.add(neededText);
                }
                if (temp.startsWith("*") && temp.length() > 1) {
                    timePeriod = temp.substring(1);
                    dateFrom = TimePeriodFrom();
                    dateTo = TimePeriodTo();
                }
            }
        } catch (IOException e) {
            throw new InvalidInputTxtException("Ошибка чтения файла " + inputFilePath, e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
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

    private void SearchAndWrite() throws InvalidInputTxtException, IOException, ParseException {
        if (inputLogPathList.isEmpty() || neededTextList.isEmpty()) {   //проверяем, что есть хоть один путь и хоть один искомый текст
            throw new InvalidInputTxtException("Не заданы параметры для работы программы");
        }
        for (String inputLogPathElement : inputLogPathList) {    //проверяем, существуют ли файлы по всем искомым путям, где искать мы будем
            if (Files.notExists(Path.of(inputLogPathElement))) {
                throw new InvalidInputTxtException("Не существует файла " + inputLogPathElement + ", в котором я буду искать.");
            }
        }
        String resultDirectoryPath = CreateResultDirectory();
        if (timePeriod == null) {
            for (String inputLogPathElement : inputLogPathList) {
                if (isZip(inputLogPathElement)) {
                    SearchNeededTextInZipAndWrite(inputLogPathElement, resultDirectoryPath);
                } else {
                    SearchNeededTextInTxt(inputLogPathElement, resultDirectoryPath);
                    SearchAndWriteResultLogs(inputLogPathElement);
                }
            }
        } else {
            for (String inputLogPathElement : inputLogPathList) {   //ищем в каждом заданном пути искомый текст
                if (isZip(inputLogPathElement)) {
                    SearchNeededTextInZipAndWriteWithinPeriod(inputLogPathElement, resultDirectoryPath);    //тут и поиск и запись
                } else {
                    if (dateFrom == null || dateTo == null) {
                        throw new InvalidInputTxtException("Убедитесь, что период времени под знаком * заполнен.");
                    }
                    Path path = Path.of(inputLogPathElement);
                    BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
                    var lastModifiedTime = attr.lastModifiedTime();
                    Date lastModifiedDate = GetDate(lastModifiedTime);
                    if (lastModifiedDate.compareTo(dateFrom) >= 0 && lastModifiedDate.compareTo(dateTo) <= 0) {
                        SearchNeededTextInTxt(inputLogPathElement, resultDirectoryPath);
                        SearchAndWriteResultLogs(inputLogPathElement);  //и сразу записываем их в файлики
                    }
                }
            }
        }
    }

    private Date GetDate(FileTime time) throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy");
        Date date = new Date(time.toMillis());
        var dateTime = formatter.format(date);
        var result = dateTime.substring(0, 10);
        return formatter.parse(result);
    }

    private void InnerSearchAndWriteResultLogs(InputStream input) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(input);
             BufferedReader bufferedReader = new BufferedReader(reader)) {
            String line = "";
            //  int count = 0;
            while (bufferedReader.ready()) {
                var temp = bufferedReader.readLine();
                // count++;
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

    private boolean isZip(String inputLogPathElement) {
        String extension = "";
        if (inputLogPathElement.contains(".")) {
            extension = inputLogPathElement.substring(inputLogPathElement.lastIndexOf(".") + 1);
        }
        return extension.equalsIgnoreCase("ZIP");
    }

    private void SearchAndWriteResultLogs(String inputLogPathElement) throws IOException {
        if (UIDMap.isEmpty()) { //если мы не нашли нужные UIDы
            System.out.println("Искомые " + UID_MARKER + "в файле " + inputLogPathElement + " не найдены.");
            return;
        }
        try (InputStream input = Files.newInputStream(Path.of(inputLogPathElement))) {
            InnerSearchAndWriteResultLogs(input);
        } catch (Exception e) {
            System.out.println("Произошла ошибка чтения файла " + inputLogPathElement);
            e.printStackTrace();
            throw e;
        }
    }

    private Date TimePeriodFrom() throws ParseException, InvalidInputTxtException {
        DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        if (timePeriod.contains("-")) {
            int delimit = timePeriod.indexOf("-");
            String from = timePeriod.substring(0, delimit);
            if (from.length() >= 8) {
                return dateFormat.parse(from);
            } else {
                throw new InvalidInputTxtException("Произошла ошибка. Убедитесь, что дата под маркером * введена правильно.");
            }
        } else {
            if (timePeriod.length() >= 8) {
                return dateFormat.parse(timePeriod);
            } else {
                throw new InvalidInputTxtException("Произошла ошибка. Убедитесь, что дата под маркером * введена правильно. Необходим формат dd.MM.yyyy");
            }
        }
    }

    private Date TimePeriodTo() throws ParseException, InvalidInputTxtException {
        DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        if (timePeriod.contains("-")) {
            int delimit = timePeriod.indexOf("-");
            String to = timePeriod.substring(delimit + 1);
            if (to.length() >= 8) {
                return dateFormat.parse(to);
            } else {
                throw new InvalidInputTxtException("Произошла ошибка. Убедитесь, что дата под маркером * введена правильно. Необходим формат dd.MM.yyyy");
            }
        } else {
            if (timePeriod.length() >= 8) {
                return dateFormat.parse(timePeriod);
            } else {
                throw new InvalidInputTxtException("Произошла ошибка. Убедитесь, что дата под маркером * введена правильно.");
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

    private void SearchNeededTextInZipAndWriteWithinPeriod(String inputLogPathElement, String resultDirectoryPath) throws IOException, InvalidInputTxtException {
        if (dateFrom == null || dateTo == null) {
            throw new InvalidInputTxtException("Убедитесь, что период времени под знаком * заполнен.");
        }
        try (ZipFile zipFile = new ZipFile(inputLogPathElement)) {
            final Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                var lastModifiedTime = zipEntry.getLastModifiedTime();
                if (lastModifiedTime == null) {
                    System.out.println("Не удается получить дату изменения файла " + zipEntry.toString() + " внутри zip файла " + inputLogPathElement);
                    throw new IOException();
                }
                Date lastModifiedDate = GetDate(lastModifiedTime);
                if (lastModifiedDate.compareTo(dateFrom) < 0 || lastModifiedDate.compareTo(dateTo) > 0)
                    continue;
                try (InputStream input = zipFile.getInputStream(zipFile.getEntry(zipEntry.getName()))) {
                    InnerSearchNeededTextInFile(input, resultDirectoryPath);
                    try (InputStream input2 = zipFile.getInputStream(zipFile.getEntry(zipEntry.getName()))) {
                        InnerSearchAndWriteResultLogs(input2);
                    }
                } catch (IOException e) {
                    System.out.println("Произошла ошибка чтения архива: " + zipEntry);
                    e.printStackTrace();
                    throw e;
                } catch (Exception e) {
                    System.out.println("Произошла ошибка. Подробности: ");
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    private void SearchNeededTextInZipAndWrite(String inputLogPathElement, String resultDirectoryPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(inputLogPathElement)) {
            final Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                try (InputStream input = zipFile.getInputStream(zipFile.getEntry(zipEntry.getName()))) {
                    InnerSearchNeededTextInFile(input, resultDirectoryPath);
                    try (InputStream input2 = zipFile.getInputStream(zipFile.getEntry(zipEntry.getName()))) {
                        InnerSearchAndWriteResultLogs(input2);
                    }
                } catch (IOException e) {
                    System.out.println("Произошла ошибка чтения архива: " + zipEntry);
                    e.printStackTrace();
                    throw e;
                } catch (Exception e) {
                    System.out.println("Произошла ошибка. Подробности: ");
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
        }

    }

    private void SearchNeededTextInTxt(String inputLogPathElement, String resultDirectoryPath) throws IOException {
        Path of = Path.of(inputLogPathElement);
        try (InputStream input = Files.newInputStream(of)) {
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

