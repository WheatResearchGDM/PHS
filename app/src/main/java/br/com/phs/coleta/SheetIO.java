package br.com.phs.coleta;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.*;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public final class SheetIO {
  private static XmlPullParser parser(InputStream stream) throws Exception {
    XmlPullParser p = XmlPullParserFactory.newInstance().newPullParser();
    p.setInput(stream, "UTF-8");
    return p;
  }

  private static String key(String s) {
    return Normalizer.normalize(s.replace("\uFEFF", "").trim(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toUpperCase(Locale.ROOT);
  }

  public static List<Domain.Sample> read(File file, boolean csv) throws Exception {
    List<List<String>> rows = csv ? readCsv(file) : readXlsx(file);
    if (rows.isEmpty()) throw new IOException("O arquivo está vazio.");
    List<String> header = rows.get(0);
    int[] positions = new int[6];
    for (int i = 0; i < 6; i++) {
      positions[i] = -1;
      for (int j = 0; j < header.size(); j++)
        if (key(header.get(j)).equals(key(Domain.INPUT[i]))) {
          if (positions[i] >= 0) throw new IOException("Cabeçalho duplicado: " + Domain.INPUT[i]);
          positions[i] = j;
        }
      if (positions[i] < 0) throw new IOException("Coluna obrigatória ausente: " + Domain.INPUT[i]);
    }
    List<Domain.Sample> result = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (int r = 1; r < rows.size(); r++) {
      List<String> row = rows.get(r);
      if (row.stream().allMatch(String::isBlank)) continue;
      String[] values = new String[6];
      for (int c = 0; c < 6; c++)
        values[c] = positions[c] < row.size() ? row.get(positions[c]).trim() : "";
      values[0] = Domain.cleanId(values[0]);
      values[4] = Domain.cleanId(values[4]);
      if (values[0].trim().isEmpty()) throw new IOException("FEID vazio na linha " + (r + 1) + ".");
      if (!ids.add(values[0]))
        throw new IOException(
            "FEID repetido: " + values[0] + ". Corrija a entrada antes de importar.");
      if (values[3].trim().isEmpty() || values[4].trim().isEmpty())
        throw new IOException("Variedade ou parcela ausente na linha " + (r + 1) + ".");
      result.add(
          new Domain.Sample(
              values[0], values[1], values[2], values[3], values[4], Domain.cleanId(values[5])));
    }
    if (result.isEmpty()) throw new IOException("Nenhum genótipo encontrado.");
    return result;
  }

  private static List<List<String>> readXlsx(File file) throws Exception {
    try (ZipFile zip = new ZipFile(file)) {
      long total = 0;
      for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
        long size = e.nextElement().getSize();
        if (size > 100_000_000) throw new IOException("Planilha muito grande.");
        total += Math.max(size, 0);
        if (total > 150_000_000) throw new IOException("Planilha muito grande.");
      }
      List<String> strings = new ArrayList<>();
      ZipEntry shared = zip.getEntry("xl/sharedStrings.xml");
      if (shared != null)
        try (InputStream in = zip.getInputStream(shared)) {
          XmlPullParser p = parser(in);
          StringBuilder text = null;
          for (int event = p.getEventType();
              event != XmlPullParser.END_DOCUMENT;
              event = p.next()) {
            if (event == XmlPullParser.START_TAG && p.getName().equals("si"))
              text = new StringBuilder();
            else if (event == XmlPullParser.START_TAG && p.getName().equals("t") && text != null)
              text.append(p.nextText());
            else if (event == XmlPullParser.END_TAG && p.getName().equals("si")) {
              strings.add(text.toString());
              text = null;
            }
          }
        }
      String relation = null;
      try (InputStream in = zip.getInputStream(required(zip, "xl/workbook.xml"))) {
        XmlPullParser p = parser(in);
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next())
          if (ev == XmlPullParser.START_TAG && p.getName().equals("sheet")) {
            for (int a = 0; a < p.getAttributeCount(); a++)
              if (p.getAttributeName(a).equals("r:id") || p.getAttributeName(a).equals("id"))
                relation = p.getAttributeValue(a);
            break;
          }
      }
      String target = "worksheets/sheet1.xml";
      try (InputStream in = zip.getInputStream(required(zip, "xl/_rels/workbook.xml.rels"))) {
        XmlPullParser p = parser(in);
        for (int ev = p.getEventType(); ev != XmlPullParser.END_DOCUMENT; ev = p.next())
          if (ev == XmlPullParser.START_TAG
              && p.getName().equals("Relationship")
              && Objects.equals(relation, p.getAttributeValue(null, "Id")))
            target = p.getAttributeValue(null, "Target");
      }
      String sheet = target.startsWith("/") ? target.substring(1) : "xl/" + target;
      List<List<String>> rows = new ArrayList<>();
      try (InputStream in = zip.getInputStream(required(zip, sheet))) {
        XmlPullParser p = parser(in);
        List<String> row = null;
        String type = "", value = "";
        int column = 0;
        for (int event = p.getEventType(); event != XmlPullParser.END_DOCUMENT; event = p.next()) {
          if (event == XmlPullParser.START_TAG) {
            String tag = p.getName();
            if (tag.equals("row")) {
              row = new ArrayList<>();
              column = 0;
            } else if (tag.equals("c")) {
              type = p.getAttributeValue(null, "t");
              value = "";
              String ref = p.getAttributeValue(null, "r");
              if (ref != null) {
                column = 0;
                for (int k = 0; k < ref.length() && Character.isLetter(ref.charAt(k)); k++)
                  column = column * 26 + (ref.charAt(k) - 'A' + 1);
                column--;
                if (column > 16383) throw new IOException("Coluna inválida.");
              }
            } else if (tag.equals("v")) value = p.nextText();
            else if (tag.equals("t")) value += p.nextText();
          } else if (event == XmlPullParser.END_TAG && p.getName().equals("c") && row != null) {
            if ("s".equals(type)) {
              int index = Integer.parseInt(value);
              value = strings.get(index);
            } else if ((type == null || type.equals("n")) && !value.trim().isEmpty()) {
              try {
                value = new BigDecimal(value).stripTrailingZeros().toPlainString();
              } catch (NumberFormatException ignored) {
              }
            }
            while (row.size() <= column) row.add("");
            row.set(column++, value);
          } else if (event == XmlPullParser.END_TAG && p.getName().equals("row") && row != null) {
            if (!row.stream().allMatch(String::isBlank) || rows.isEmpty()) rows.add(row);
            row = null;
            if (rows.size() > 100_001)
              throw new IOException("Limite de 100 mil registros por entrada.");
          }
        }
      }
      return rows;
    }
  }

  private static ZipEntry required(ZipFile zip, String name) throws IOException {
    ZipEntry entry = zip.getEntry(name);
    if (entry == null) throw new IOException("Estrutura Excel inválida: " + name);
    return entry;
  }

  private static List<List<String>> readCsv(File file) throws IOException {
    String content;
    try (InputStream in = new FileInputStream(file);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      byte[] block = new byte[8192];
      for (int n; (n = in.read(block)) != -1; ) bytes.write(block, 0, n);
      content = bytes.toString(StandardCharsets.UTF_8.name());
    }
    if (content.indexOf('\uFFFD') >= 0) throw new IOException("Salve o CSV na codificação UTF-8.");
    String first = content.split("\\R", 2)[0];
    char separator = first.contains(";") ? ';' : ',';
    return parseCsv(content, separator);
  }

  public static List<List<String>> parseCsv(String content, char separator) throws IOException {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < content.length(); i++) {
      char ch = content.charAt(i);
      if (ch == '"') {
        if (quoted && i + 1 < content.length() && content.charAt(i + 1) == '"') {
          cell.append('"');
          i++;
        } else quoted = !quoted;
      } else if (ch == separator && !quoted) {
        row.add(cell.toString());
        cell.setLength(0);
      } else if ((ch == '\n' || ch == '\r') && !quoted) {
        if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
        row.add(cell.toString());
        cell.setLength(0);
        rows.add(row);
        row = new ArrayList<>();
      } else cell.append(ch);
    }
    if (quoted) throw new IOException("CSV com aspas não fechadas.");
    if (cell.length() > 0 || !row.isEmpty()) {
      row.add(cell.toString());
      rows.add(row);
    }
    return rows;
  }

  public static void write(OutputStream stream, List<Domain.Reading> readings, boolean csv)
      throws IOException {
    if (csv) {
      Writer out = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
      out.write('\uFEFF');
      csvRow(out, Arrays.asList(Domain.OUTPUT));
      for (Domain.Reading r : readings) csvRow(out, values(r));
      out.flush();
      return;
    }
    try (ZipOutputStream zip = new ZipOutputStream(stream)) {
      entry(
          zip,
          "[Content_Types].xml",
          "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default"
              + " Extension=\"rels\""
              + " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default"
              + " Extension=\"xml\" ContentType=\"application/xml\"/><Override"
              + " PartName=\"/xl/workbook.xml\""
              + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override"
              + " PartName=\"/xl/worksheets/sheet1.xml\""
              + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override"
              + " PartName=\"/xl/styles.xml\""
              + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
      entry(
          zip,
          "_rels/.rels",
          "<Relationships"
              + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship"
              + " Id=\"rId1\""
              + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\""
              + " Target=\"xl/workbook.xml\"/></Relationships>");
      entry(
          zip,
          "xl/workbook.xml",
          "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
              + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet"
              + " name=\"Dados\" sheetId=\"1\" r:id=\"rId1\"/></sheets><calcPr"
              + " fullCalcOnLoad=\"1\"/></workbook>");
      entry(
          zip,
          "xl/_rels/workbook.xml.rels",
          "<Relationships"
              + " xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship"
              + " Id=\"rId1\""
              + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\""
              + " Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\""
              + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\""
              + " Target=\"styles.xml\"/></Relationships>");
      entry(
          zip,
          "xl/styles.xml",
          "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><numFmts"
              + " count=\"3\"><numFmt numFmtId=\"164\" formatCode=\"dd/mm/yyyy\"/><numFmt"
              + " numFmtId=\"165\" formatCode=\"dd/mm/yyyy hh:mm:ss\"/><numFmt numFmtId=\"166\""
              + " formatCode=\"0.00\"/></numFmts><fonts count=\"2\"><font><sz val=\"11\"/><name"
              + " val=\"Calibri\"/></font><font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name"
              + " val=\"Calibri\"/></font></fonts><fills count=\"3\"><fill><patternFill"
              + " patternType=\"none\"/></fill><fill><patternFill"
              + " patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor"
              + " rgb=\"FF09243B\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders"
              + " count=\"1\"><border/></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\""
              + " fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"5\"><xf"
              + " numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf"
              + " numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\""
              + " applyFill=\"1\"/><xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\""
              + " xfId=\"0\" applyNumberFormat=\"1\"/><xf numFmtId=\"165\" fontId=\"0\""
              + " fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/><xf"
              + " numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\""
              + " applyNumberFormat=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle"
              + " name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
      StringBuilder xml =
          new StringBuilder(
              "<worksheet"
                  + " xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView"
                  + " workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\""
                  + " state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"20\""
                  + " width=\"19\" customWidth=\"1\"/></cols><sheetData><row r=\"1\">");
      for (int c = 0; c < 20; c++) textCell(xml, c, 1, Domain.OUTPUT[c], 1);
      xml.append("</row>");
      int row = 2;
      for (Domain.Reading r : readings) {
        xml.append("<row r=\"").append(row).append("\">");
        List<String> values = values(r);
        for (int c = 0; c < 20; c++) {
          String ref = column(c) + row;
          String value = values.get(c);
          if (c >= 10 && c <= 15)
            numeric(xml, ref, value, 0, c == 15 ? "SUM(K" + row + ":O" + row + ")" : null);
          else if (c == 16) {
            Double mean = Domain.mean(r.counts());
            if (mean != null)
              numeric(
                  xml,
                  ref,
                  mean.toString(),
                  4,
                  "IF(P"
                      + row
                      + "=0,\"\",(K"
                      + row
                      + "+L"
                      + row
                      + "*3+M"
                      + row
                      + "*5+N"
                      + row
                      + "*7+O"
                      + row
                      + "*9)/P"
                      + row
                      + ")");
          } else if (c == 6 || c == 7 || c == 8) {
            if (!value.trim().isEmpty())
              numeric(
                  xml,
                  ref,
                  Long.toString(
                      ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), LocalDate.parse(value))),
                  2,
                  null);
          } else if (c == 17) {
            LocalDateTime date = LocalDateTime.parse(value);
            double serial =
                ChronoUnit.DAYS.between(LocalDate.of(1899, 12, 30), date.toLocalDate())
                    + date.toLocalTime().toSecondOfDay() / 86400.0;
            numeric(xml, ref, Double.toString(serial), 3, null);
          } else textCell(xml, c, row, value, 0);
        }
        xml.append("</row>");
        row++;
      }
      xml.append("</sheetData><autoFilter ref=\"A1:T").append(row - 1).append("\"/></worksheet>");
      entry(zip, "xl/worksheets/sheet1.xml", xml.toString());
    }
  }

  private static List<String> values(Domain.Reading r) {
    List<String> values = new ArrayList<>(Arrays.asList(r.sample().fields()));
    values.add(r.harvest());
    values.add(r.assembly());
    values.add(r.day());
    values.add(r.observation());
    for (int n : r.counts()) values.add(Integer.toString(n));
    values.add(Integer.toString(Domain.total(r.counts())));
    Double mean = Domain.mean(r.counts());
    values.add(mean == null ? "" : mean.toString());
    values.add(r.timestamp());
    values.add(r.user());
    values.add(r.deviceId());
    return values;
  }

  private static void csvRow(Writer writer, List<String> row) throws IOException {
    for (int c = 0; c < row.size(); c++) {
      if (c > 0) writer.write(';');
      writer.write('"');
      writer.write(row.get(c).replace("\"", "\"\""));
      writer.write('"');
    }
    writer.write("\r\n");
  }

  private static String esc(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
  }

  private static String column(int index) {
    String s = "";
    for (int n = index + 1; n > 0; n = (n - 1) / 26) s = (char) ('A' + (n - 1) % 26) + s;
    return s;
  }

  private static void textCell(StringBuilder xml, int col, int row, String value, int style) {
    xml.append("<c r=\"")
        .append(column(col))
        .append(row)
        .append("\" s=\"")
        .append(style)
        .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
        .append(esc(value))
        .append("</t></is></c>");
  }

  private static void numeric(
      StringBuilder xml, String ref, String value, int style, String formula) {
    xml.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\">");
    if (formula != null) xml.append("<f>").append(esc(formula)).append("</f>");
    xml.append("<v>").append(value).append("</v></c>");
  }

  private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(
        ("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + content)
            .getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private SheetIO() {}
}
