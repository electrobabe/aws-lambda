package at.electrobabe.utils.calendar;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * integration test
 */
public class CalendarUtilsTest {
    private static final String DATE = "2021-04-21";
    private static final String PROPERTY_FILE_NAME = "calendarUtilsTest.properties";

    private final Properties prop = new Properties();


    @Before
    public void setup() {
        try {
            //load a properties file from class path, inside static method
            prop.load(CalendarUtilsTest.class.getClassLoader().getResourceAsStream(PROPERTY_FILE_NAME));
        } catch (IOException ex) {
            fail();
        }
    }


    @Test
    public void getCalendarEntriesForDay() {
        String webcalUrl = prop.getProperty("WEBCAL_URL", "https://outlook.office365.com/owa/calendar/123/calendar.ics");

        String ret = CalendarUtils.getCalendarEntriesForDay(DATE, webcalUrl);
        assertNotNull(ret);
    }
}