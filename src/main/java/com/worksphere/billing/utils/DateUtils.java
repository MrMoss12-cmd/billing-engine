package com.worksphere.billing.utils;

import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

/**
 * Utilidades para manejo y transformación de fechas.
 */
@Component
public class DateUtils {

    private static final Logger logger = Logger.getLogger(DateUtils.class.getName());

    /**
     * Convierte un Instant a ZonedDateTime en la zona horaria especificada.
     *
     * @param instant   Instante a convertir
     * @param zoneIdStr Zona horaria (ej. "America/Bogota")
     * @return ZonedDateTime en la zona horaria indicada
     */
    public ZonedDateTime toZonedDateTime(Instant instant, String zoneIdStr) {
        ZoneId zoneId = ZoneId.of(zoneIdStr);
        ZonedDateTime zonedDateTime = instant.atZone(zoneId);
        logger.fine("Conversión de Instant a ZonedDateTime: instant=" + instant + ", zoneId=" + zoneIdStr);
        return zonedDateTime;
    }

    /**
     * Calcula la fecha de vencimiento a partir de la fecha de inicio y número de días.
     *
     * @param startDate Fecha de inicio
     * @param days      Número de días para vencimiento
     * @return Fecha de vencimiento
     */
    public LocalDate calculateDueDate(LocalDate startDate, int days) {
        LocalDate dueDate = startDate.plusDays(days);
        logger.fine("Cálculo de vencimiento: startDate=" + startDate + ", days=" + days + ", dueDate=" + dueDate);
        return dueDate;
    }

    /**
     * Calcula la cantidad de días entre dos fechas, considerando zonas horarias.
     *
     * @param start Fecha de inicio
     * @param end   Fecha de fin
     * @return Número de días entre las fechas
     */
    public long daysBetween(ZonedDateTime start, ZonedDateTime end) {
        long days = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate());
        logger.fine("Cálculo de días entre fechas: start=" + start + ", end=" + end + ", days=" + days);
        return days;
    }

    /**
     * Obtiene el inicio del ciclo de facturación mensual para una fecha dada.
     *
     * @param date Fecha de referencia
     * @return Primer día del mes de la fecha
     */
    public LocalDate getBillingCycleStart(LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        logger.fine("Inicio del ciclo de facturación: date=" + date + ", start=" + start);
        return start;
    }

    /**
     * Obtiene el fin del ciclo de facturación mensual para una fecha dada.
     *
     * @param date Fecha de referencia
     * @return Último día del mes de la fecha
     */
    public LocalDate getBillingCycleEnd(LocalDate date) {
        LocalDate end = date.withDayOfMonth(date.lengthOfMonth());
        logger.fine("Fin del ciclo de facturación: date=" + date + ", end=" + end);
        return end;
    }

    /**
     * Convierte LocalDate a String con formato ISO.
     *
     * @param date LocalDate a convertir
     * @return String en formato yyyy-MM-dd
     */
    public String formatDate(LocalDate date) {
        String formatted = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        logger.fine("Formato de fecha: date=" + date + ", formatted=" + formatted);
        return formatted;
    }

    /**
     * Convierte ZonedDateTime a String con formato ISO.
     *
     * @param dateTime ZonedDateTime a convertir
     * @return String en formato yyyy-MM-dd'T'HH:mm:ssXXX
     */
    public String formatDateTime(ZonedDateTime dateTime) {
        String formatted = dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        logger.fine("Formato de fecha/hora: dateTime=" + dateTime + ", formatted=" + formatted);
        return formatted;
    }

    /**
     * Obtiene la fecha y hora actual en la zona horaria especificada.
     *
     * @param zoneIdStr Zona horaria
     * @return ZonedDateTime actual
     */
    public ZonedDateTime now(String zoneIdStr) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(zoneIdStr));
        logger.fine("Fecha/hora actual: zoneId=" + zoneIdStr + ", now=" + now);
        return now;
    }
}
