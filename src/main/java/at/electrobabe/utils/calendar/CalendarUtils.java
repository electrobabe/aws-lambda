package at.electrobabe.utils.calendar;

import lombok.extern.slf4j.Slf4j;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.*;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.util.MapTimeZoneCache;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * read a ics file from the web and filter by items on a certain day
 */
@Slf4j
public class CalendarUtils {
    
    private static final long ONE_DAY_IN_MILLIS = 86400000;
    private static final String DATE_FORMAT = "yyyy-MM-dd";


    public static String getCalendarEntriesForDay(String date, String url) {
        StringBuilder ret = new StringBuilder();
        Calendar cal = CalendarUtils.getCalendar(url);

        List<VEvent> events = CalendarUtils.getEventsForDate(cal, date);
        for (VEvent e : events) {
            ret.append(CalendarUtils.printEvent(e)).append("\n");
        }
        return ret.toString();
    }

    private static Calendar getCalendar(String urlString) {
        try {
            // instead of ical4j.properties
            System.setProperty("net.fortuna.ical4j.timezone.cache.impl", MapTimeZoneCache.class.getName());

            URL url = new URL(urlString);
            URLConnection conn = url.openConnection();

            CalendarBuilder builder = new CalendarBuilder();
            return builder.build(conn.getInputStream());

        } catch (IOException | ParserException e) {
            log.error("error reading calendar", e);
            return null;
        }
    }

    private static List<VEvent> getEventsForDate(Calendar calendar, String dateStr) {
        List<VEvent> list = new ArrayList<>();
        log.info("search for date: '{}'", dateStr);

        Date date;
        try {
            date = new Date(dateStr, DATE_FORMAT);
        } catch (ParseException e) {
            log.error("error parsing date", e);
            return list;
        }

        ComponentList<CalendarComponent> components = calendar.getComponents();

        // needs format yyyy-MM-dd
        String plainDateStr = dateStr.replace("-", "");
                
        for (CalendarComponent c : components) {
            if (c instanceof VEvent) {
                VEvent event = (VEvent) c;

                addByDate(plainDateStr, list, date, event);

            } else if (c instanceof VTimeZone) {
                VTimeZone t = (VTimeZone) c;
                log.debug("this is a time zone: {}", t.getTimeZoneId());
            } else {
                log.warn("what are you?? {}", c.getClass());
            }
        }


        return list;
    }

    private static void addByDate(String dateStr, List<VEvent> list, Date date, VEvent event) {
        if (matchesDate(dateStr, event) && !inList(list, event)) {
            list.add(event);
            log.debug("single event added: {}", event.getSummary().getValue());

        } else if (event.getSequence() != null) {
            addOccurence(dateStr, list, date, event);
        }
    }

    private static boolean inList(List<VEvent> list, VEvent event) {
        return list.stream().anyMatch(o -> o.getSummary().getValue().equals(event.getSummary().getValue()));
    }

    private static void addOccurence(String dateStr, List<VEvent> list, Date date, VEvent event) {
        try {
            // fixes: VEvent occurence = event.getOccurrence(date);
            PeriodList periods = event.getConsumedTime(date, new Date(date.getTime() + ONE_DAY_IN_MILLIS));

            for (final Period p : periods) {
                if (p.getStart().toString().startsWith(dateStr) && !inList(list, event)) {
                    final VEvent occurrence = (VEvent) event.copy();
                    occurrence.getProperties().add(new RecurrenceId(date));
                    list.add(occurrence);

                    log.debug("recurring event added: {}", event.getSummary().getValue());
                }
            }

        } catch (IOException | URISyntaxException | ParseException e) {
            log.error("error getting occurence", e);
        }
    }

    private static boolean matchesDate(String date, VEvent event) {
        return event.getStartDate().getDate().toString().startsWith(date);
    }

    /**
     * see also https://dzone.com/articles/how-to-format-a-string-clarified
     * 
     * @param event VEvent
     * @return formatted String
     */
    private static String printEvent(VEvent event) {
        String outOfOffice = event.getProperty("X-MICROSOFT-CDO-BUSYSTATUS") != null ? event.getProperty("X-MICROSOFT-CDO-BUSYSTATUS").getValue() : "";
        String text = String.format("%s  %s  (%s)", getDurationAsString(event), event.getSummary().getValue(), outOfOffice);
        log.info(text);

        return text;
    }

    private static void printEvent(VEvent event, boolean printOutOfOffice) {
        String outOfOffice = event.getProperty("X-MICROSOFT-CDO-BUSYSTATUS") != null ? event.getProperty("X-MICROSOFT-CDO-BUSYSTATUS").getValue() : "";
        if (printOutOfOffice) {
            log.info("event: {} '{}' ({})", getDurationAsString(event), event.getSummary().getValue(), outOfOffice);
        } else if (!outOfOffice.equals("OOF")) {
            log.info("event: {} '{}'", getDurationAsString(event), event.getSummary().getValue());
        }
    }

    private static long getDuration(VEvent event) {
        return event.getEndDate().getDate().getTime() - event.getStartDate().getDate().getTime();
    }

    private static String getDurationAsString(VEvent event) {
        return formatMillis(getDuration(event));
    }

    private static String formatMillis(long durationInMillis) {
        long hours = TimeUnit.MILLISECONDS.toHours(durationInMillis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(durationInMillis) % 60;

        return String.format("%dh %2dm", hours, minutes);
    }
}
