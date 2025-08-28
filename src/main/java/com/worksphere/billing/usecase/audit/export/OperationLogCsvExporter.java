package com.worksphere.billing.usecase.audit.export;

import com.worksphere.billing.domain.model.BillingOperationLog;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/** Exportador simple a CSV (UTF-8, encabezado, seguro para grandes volúmenes en streaming si se usa por chunks). */
public final class OperationLogCsvExporter {

    private OperationLogCsvExporter() {}

    public static byte[] toCsv(List<BillingOperationLog> logs) {
        DateTimeFormatter fmt = DateTimeFormatter.ISO_INSTANT;
        String header = "id,tenant_id,billing_cycle_id,invoice_id,operation_type,actor,timestamp,details";

        String body = logs.stream()
                .map(l -> String.join(",",
                        csv(l.getId()),
                        csv(l.getTenantId()),
                        csv(l.getBillingCycleId()),
                        csv(nullToEmpty(l.getInvoiceId())),
                        csv(l.getOperationType()),
                        csv(l.getActor()),
                        csv(fmt.format(l.getTimestamp())),
                        csv(nullToEmpty(l.getDetails()))
                ))
                .collect(Collectors.joining("\n"));

        String csv = header + "\n" + body;
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    private static String csv(String v) {
        String s = v == null ? "" : v;
        // escape básico CSV: comillas dobles y comas
        s = s.replace("\"", "\"\"");
        return "\"" + s + "\"";
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
