import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import jxl.Cell;
import jxl.Sheet;
import jxl.Workbook;
import jxl.format.BorderLineStyle;
import jxl.read.biff.BiffException;
import jxl.write.Border;
import jxl.write.Colour;
import jxl.write.Formula;
import jxl.write.Label;
import jxl.write.Number;
import jxl.write.WritableCellFormat;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import jxl.write.biff.RowsExceededException;

public class WriteExcel {

	// company path and name
	private String path, CompanyName;

	// each feature represented by a column
	private String[] features;

	// book to write in
	private WritableWorkbook workbook;

	// current writable sheet
	private WritableSheet sheet;

	private int specialCol = Global.specialCell;
	private int lag_var = Global.lag_var;

	private String[] price_cols = new String[2 * lag_var + 1];
	private String[] volume_cols = new String[2 * lag_var + 1];

	public void passFeatures(String[] features) throws IOException {
		this.features = features;
	}

	public void setOutputFile(String companyPath, String CompanyName)
			throws IOException {
		this.path = companyPath;
		this.CompanyName = CompanyName;
	}

	public void createExcel() throws Exception {
		File file = new File(path);
		workbook = Workbook.createWorkbook(file);

		workbook.createSheet("Twitter", 0);
		workbook.createSheet("StockTwits", 1);
		workbook.createSheet("Combined Data", 2);

		for (int i = 0; i < 3; i++) {
			sheet = workbook.getSheet(i);
			sheet.getSettings().setDefaultColumnWidth(Global.COLWIDTH);
			writeFeatures();
			adddummyDays();
		}

		writeAndClose();
	}

	/*
	 * add dummy days at start of sheet with no features
	 */
	private void adddummyDays() throws Exception {
		String Start = Global.startDate;
		Date date = Global.sdf.parse(Start);
		double v[] = new double[0];
		int cnt = 0, i;
		for (i = 1; i < 20; i++) {
			Date d = new Date(date.getTime() - TimeUnit.DAYS.toMillis(i));
			double[] D = read(Global.sdf.format(d));
			if (D[0] != -1)
				cnt++;
			if (cnt == lag_var)
				break;
		}
		for (; i > 0; i--) {
			Date d = new Date(date.getTime() - TimeUnit.DAYS.toMillis(i));
			addNewDay(Global.sdf.format(d), v);
		}

	}


	/*
	 * add dummy days at end of sheet with no features
	 */
	
	public void adddummyDaysAtEnd() throws Exception {
		int size = getRowsCnt();
		String lastDay = sheet.getCell(0, size - 1).getContents();
		Date date = Global.sdf.parse(lastDay);
		double v[] = new double[0];

		int cnt = 0, i;
		for (i = 1; i < 20; i++) {
			Date d = new Date(date.getTime() + TimeUnit.DAYS.toMillis(i));
			double[] D = read(Global.sdf.format(d));
			if (D[0] != -1) {
				if (addNewDay(Global.sdf.format(d), v))
					cnt++;
			}
			if (cnt == Global.lag_var)
				break;
		}
	}

	public void initializeExcelSheet(int sheetNum) throws IOException,
			WriteException, BiffException {
		
		File file = new File(path);
		Workbook myWorkbook = Workbook.getWorkbook(file);
		workbook = Workbook.createWorkbook(file, myWorkbook);
		sheet = workbook.getSheet(sheetNum);
		
		int k = 0;
		int pos = Global.price_start_col - lag_var;
		for (int i = -lag_var; i <= lag_var; i++) {
			price_cols[k++] = convert(++pos);
		}
		k = 0;
		pos = Global.volume_start_col - lag_var;
		for (int i = -lag_var; i <= lag_var; i++) {
			volume_cols[k++] = convert(++pos);
		}
	}

	public void writeFeatures() throws WriteException {
		addCaption(0, 0, "Day");
		for (int i = 0; i < features.length; i++) {
			addCaption(i + 1, 0, features[i]);
		}
		int k = 0;
		int pos = Global.price_start_col - lag_var;
		for (int i = -lag_var; i <= lag_var; i++) {
			addCaption(pos++, 0, "price(" + i + ")");
			price_cols[k++] = convert(pos);
		}
		k = 0;
		pos = Global.volume_start_col - lag_var;
		for (int i = -lag_var; i <= lag_var; i++) {
			addCaption(pos++, 0, "volume(" + i + ")");
			volume_cols[k++] = convert(pos);
		}

		addNumber(specialCol, 0, 1.0);
	}

	public int getRowsCnt() throws Exception {
		Cell cell = sheet.getCell(specialCol, 0);
		return (int) Double.parseDouble(cell.getContents());
	}

	boolean addNewDay(String day, double[] val) throws Exception {
		double[] d = read(day);
		if (d[0] == -1 && d[1] == -1)
			return false;

		int row = getRowsCnt();
		addNumber(specialCol, 0, row + 1.0);
		addCaption(0, row, day);
		int n = val.length;
		for (int j = 0; j < n; j++)
			addNumber(j + 1, row, val[j]);

		int start_price = Global.price_start_col;
		for (int i = -lag_var; i <= lag_var; i++) {
			if (row + i > 0)
				addNumber(start_price + i, row + i, d[0]);

		}
		int start_Volume = Global.volume_start_col;
		for (int i = -lag_var; i <= lag_var; i++) {
			if (row + i > 0)
				addNumber(start_Volume + i, row + i, d[1]);

		}
		return true;
	}

	public double[] read(String dayx) throws IOException, ParseException {
		File inputWorkbook = new File(Global.historyPath + CompanyName + ".xls");
		Workbook w;
		String volume = "0", price = "0";
		try {

			w = Workbook.getWorkbook(inputWorkbook);
			// Get the first sheet
			Sheet sheet = w.getSheet(0);
			// Loop over first 10 column and lines
			for (int i = 1; i < sheet.getRows(); i++) {
				Cell cell = sheet.getCell(0, i);
				String day2 = cell.getContents();
				if (Global.areEquals(day2, dayx)) {
					volume = sheet.getCell(5, i).getContents();
					price = sheet.getCell(6, i).getContents();
				}
			}
		} catch (BiffException e) {
			e.printStackTrace();
		}

		double a = Double.parseDouble(price);
		double b = Double.parseDouble(volume);

		return new double[] { a, b };
	}

	void writeAndClose() throws WriteException, IOException {
		workbook.write();
		workbook.close();
	}

	void calcCorrel(int col, int row, String ch1, String ch2, int start, int end)
			throws WriteException {
		WritableCellFormat cellFormat = new WritableCellFormat();

		cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN);
		StringBuffer buf = new StringBuffer();
		String s1 = ch1 + "" + start + ":" + ch1 + "" + end;
		String s2 = ch2 + "" + start + ":" + ch2 + "" + end;
		buf.append("CORREL(" + s1 + "," + s2 + ")");
		Formula f = new Formula(col, row, buf.toString(), cellFormat);
		sheet.addCell(f);
	}

	private void addCaption(int column, int row, String s)
			throws RowsExceededException, WriteException {
		WritableCellFormat cellFormat = new WritableCellFormat();

		cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN);
		cellFormat.setBackground(Colour.GRAY_25);
		sheet.addCell(new Label(column, row, s, cellFormat));

	}

	private void addNumber(int column, int row, Double d)
			throws WriteException, RowsExceededException {
		WritableCellFormat cellFormat = new WritableCellFormat();

		cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN);
		sheet.addCell(new Number(column, row, d, cellFormat));
	}

	private void addLabel(int column, int row, String s) throws WriteException,
			RowsExceededException {
		WritableCellFormat cellFormat = new WritableCellFormat();
		cellFormat.setBorder(Border.ALL, BorderLineStyle.THIN);
		sheet.addCell(new Label(column, row, s, cellFormat));
	}

	// feature = 0 then price else volume
	public void drawTable1() throws Exception {
		int frow = getRowsCnt() + 5, fcolumn = 1;

		addLabel(fcolumn, frow, "Features\\Lag");
		int r = frow + 1;
		for (int i = 0; i < features.length; i++) {
			addCaption(fcolumn, r++, features[i]);
		}

		int pos = fcolumn + 1;
		for (int i = -lag_var; i <= lag_var; i++) {
			addCaption(pos++, frow, "price(" + i + ")");
		}

		int cnt = 1;
		for (char ch2 = 'B'; ch2 < 'O'; ch2++) {
			int index = 0;
			pos = fcolumn + 1;
			for (int i = -lag_var; i <= lag_var; i++) {
				String ch1 = price_cols[index++];
				calcCorrel(pos++, frow + cnt, ch1, ch2 + "", lag_var + 2,
						getRowsCnt());

			}
			cnt++;
		}
	}

	public void drawTable2() throws Exception {
		int frow = getRowsCnt() + features.length + 10, fcolumn = 1;
		addLabel(fcolumn, frow, "Features\\Lag");
		int r = frow + 1;
		for (int i = 0; i < features.length; i++) {
			addCaption(fcolumn, r++, features[i]);
		}

		int pos = fcolumn + 1;
		for (int i = -lag_var; i <= lag_var; i++) {
			addCaption(pos++, frow, "volume(" + i + ")");
		}

		int cnt = 1;
		for (char ch2 = 'B'; ch2 < 'O'; ch2++) {
			int index = 0;
			pos = fcolumn + 1;
			for (int i = -lag_var; i <= lag_var; i++) {
				String ch1 = volume_cols[index++];
				calcCorrel(pos++, frow + cnt, ch1, ch2 + "", lag_var + 2,
						getRowsCnt());
			}
			cnt++;
		}
	}

	public void drawTables() throws Exception {

		drawTable1();
		drawTable2();

	}

	String convert(int a) {
		int k = 1;
		while (a >= k) {
			a -= k;
			k *= 26;
		}
		k /= 26;
		String s = "";

		while (k > 0) {
			s += ((char) ('A' + (a / k)));
			a %= k;
			k /= 26;
		}
		return s;
	}

}