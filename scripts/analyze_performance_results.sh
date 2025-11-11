#!/bin/bash
# 📊 Script para analizar reportes de Locust y generar resumen de métricas

RESULTS_DIR="${1:-test-results/performance}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  📊 ANÁLISIS DE PERFORMANCE TESTS - LOCUST"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

if [ ! -f "$RESULTS_DIR/load_test_stats.csv" ]; then
    echo "❌ ERROR: No se encontró el archivo load_test_stats.csv"
    echo "   Ubicación esperada: $RESULTS_DIR/load_test_stats.csv"
    exit 1
fi

# Función para colorear output
green() { echo -e "\033[0;32m$1\033[0m"; }
yellow() { echo -e "\033[0;33m$1\033[0m"; }
red() { echo -e "\033[0;31m$1\033[0m"; }
blue() { echo -e "\033[0;34m$1\033[0m"; }
bold() { echo -e "\033[1m$1\033[0m"; }

# Función para evaluar métricas
evaluate_response_time() {
    local avg=$1
    if (( $(echo "$avg < 500" | bc -l) )); then
        green "✅ EXCELENTE"
    elif (( $(echo "$avg < 1000" | bc -l) )); then
        yellow "⚠️  ACEPTABLE"
    else
        red "❌ REQUIERE OPTIMIZACIÓN"
    fi
}

evaluate_throughput() {
    local rps=$1
    if (( $(echo "$rps > 50" | bc -l) )); then
        green "✅ ALTO"
    elif (( $(echo "$rps > 20" | bc -l) )); then
        yellow "⚠️  MEDIO"
    else
        red "❌ BAJO"
    fi
}

evaluate_error_rate() {
    local rate=$1
    if (( $(echo "$rate < 1" | bc -l) )); then
        green "✅ ESTABLE"
    elif (( $(echo "$rate < 5" | bc -l) )); then
        yellow "⚠️  REQUIERE ATENCIÓN"
    else
        red "❌ INESTABLE"
    fi
}

# 1. RESUMEN GENERAL
echo "$(bold '📋 1. RESUMEN GENERAL')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

TOTAL_REQUESTS=$(awk -F',' 'NR>1 {sum+=$2} END {print sum}' "$RESULTS_DIR/load_test_stats.csv")
TOTAL_FAILURES=$(awk -F',' 'NR>1 {sum+=$3} END {print sum}' "$RESULTS_DIR/load_test_stats.csv")
TOTAL_SUCCESS=$((TOTAL_REQUESTS - TOTAL_FAILURES))
OVERALL_ERROR_RATE=$(echo "scale=2; ($TOTAL_FAILURES / $TOTAL_REQUESTS) * 100" | bc)

echo "  Total Requests:    $(blue "$TOTAL_REQUESTS")"
echo "  Successful:        $(green "$TOTAL_SUCCESS")"
echo "  Failed:            $(red "$TOTAL_FAILURES")"
echo "  Error Rate:        $(blue "$OVERALL_ERROR_RATE%") $(evaluate_error_rate $OVERALL_ERROR_RATE)"
echo ""

# 2. TIEMPO DE RESPUESTA
echo "$(bold '⏱️  2. TIEMPO DE RESPUESTA (Response Times)')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "$(bold 'Endpoint')                      $(bold 'Avg')      $(bold 'Min')      $(bold 'Max')      $(bold 'p95')      $(bold 'Estado')"
echo "────────────────────────────────────────────────────────────────────────────"

awk -F',' 'NR>1 && $1!="Aggregated" {
    endpoint=$1
    avg=$4
    min=$5
    max=$6
    p95=$9
    
    printf "%-30s  %6.0fms  %6.0fms  %7.0fms  %6.0fms  ", endpoint, avg, min, max, p95
    
    if (avg < 500) {
        print "✅ EXCELENTE"
    } else if (avg < 1000) {
        print "⚠️  ACEPTABLE"
    } else {
        print "❌ LENTO"
    }
}' "$RESULTS_DIR/load_test_stats.csv"

echo ""

# 3. THROUGHPUT
echo "$(bold '🚦 3. THROUGHPUT (Requests per Second)')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "$(bold 'Endpoint')                      $(bold 'RPS')        $(bold 'Total Req')    $(bold 'Nivel')"
echo "────────────────────────────────────────────────────────────────────────────"

awk -F',' 'NR>1 && $1!="Aggregated" {
    endpoint=$1
    rps=$10
    total=$2
    
    printf "%-30s  %7.2f    %10d    ", endpoint, rps, total
    
    if (rps > 50) {
        print "✅ ALTO"
    } else if (rps > 20) {
        print "⚠️  MEDIO"
    } else {
        print "❌ BAJO"
    }
}' "$RESULTS_DIR/load_test_stats.csv"

echo ""

# 4. TASA DE ERRORES POR ENDPOINT
echo "$(bold '❌ 4. TASA DE ERRORES POR ENDPOINT')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "$(bold 'Endpoint')                      $(bold 'Success')    $(bold 'Failures')    $(bold 'Error %')    $(bold 'Estado')"
echo "────────────────────────────────────────────────────────────────────────────"

awk -F',' 'NR>1 && $1!="Aggregated" {
    endpoint=$1
    requests=$2
    failures=$3
    success=requests-failures
    
    if (requests > 0) {
        error_rate=(failures/requests)*100
    } else {
        error_rate=0
    }
    
    printf "%-30s  %8d    %9d    %7.2f%%    ", endpoint, success, failures, error_rate
    
    if (error_rate < 1) {
        print "✅ ESTABLE"
    } else if (error_rate < 5) {
        print "⚠️  ATENCIÓN"
    } else {
        print "❌ INESTABLE"
    }
}' "$RESULTS_DIR/load_test_stats.csv"

echo ""

# 5. TOP 5 ENDPOINTS MÁS LENTOS
echo "$(bold '🐌 5. TOP 5 ENDPOINTS MÁS LENTOS')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

awk -F',' 'NR>1 && $1!="Aggregated" {print $4 "," $1}' "$RESULTS_DIR/load_test_stats.csv" | \
sort -t',' -k1 -rn | head -5 | \
awk -F',' '{printf "  %2d. %-40s  %7.0fms\n", NR, $2, $1}'

echo ""

# 6. TOP 5 ENDPOINTS CON MÁS ERRORES
echo "$(bold '⚠️  6. TOP 5 ENDPOINTS CON MÁS ERRORES')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

if [ -f "$RESULTS_DIR/load_test_failures.csv" ] && [ -s "$RESULTS_DIR/load_test_failures.csv" ]; then
    awk -F',' 'NR>1 {print $3 "," $1 "," $2}' "$RESULTS_DIR/load_test_failures.csv" | \
    sort -t',' -k1 -rn | head -5 | \
    awk -F',' '{printf "  %2d. %-30s  (%d ocurrencias)\n      Error: %s\n", NR, $2, $1, $3}'
else
    echo "  $(green '✅ No se encontraron errores')"
fi

echo ""

# 7. PERCENTILES DE LATENCIA
echo "$(bold '📊 7. DISTRIBUCIÓN DE LATENCIA (Percentiles)')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "$(bold 'Endpoint')                      $(bold 'p50')     $(bold 'p75')     $(bold 'p90')     $(bold 'p95')     $(bold 'p99')"
echo "────────────────────────────────────────────────────────────────────────────"

awk -F',' 'NR>1 && $1!="Aggregated" {
    endpoint=$1
    median=$7
    p75=$8
    p90=$9
    p95=$10
    p99=$11
    
    printf "%-30s  %5.0fms  %5.0fms  %5.0fms  %5.0fms  %6.0fms\n", endpoint, median, p75, p90, p95, p99
}' "$RESULTS_DIR/load_test_stats.csv"

echo ""

# 8. RECOMENDACIONES
echo "$(bold '💡 8. RECOMENDACIONES')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Analizar y dar recomendaciones
SLOW_ENDPOINTS=$(awk -F',' 'NR>1 && $1!="Aggregated" && $4 > 1000 {count++} END {print count+0}' "$RESULTS_DIR/load_test_stats.csv")
HIGH_ERROR_ENDPOINTS=$(awk -F',' 'NR>1 && $1!="Aggregated" && ($3/$2)*100 > 5 {count++} END {print count+0}' "$RESULTS_DIR/load_test_stats.csv")
LOW_THROUGHPUT=$(awk -F',' 'NR>1 && $1!="Aggregated" && $10 < 20 {count++} END {print count+0}' "$RESULTS_DIR/load_test_stats.csv")

if [ "$SLOW_ENDPOINTS" -gt 0 ]; then
    echo "  $(red '⚠️  Se detectaron') $(red "$SLOW_ENDPOINTS") $(red 'endpoints lentos (>1s avg)')"
    echo "     → Revisar queries de BD, implementar caching"
    echo "     → Considerar optimización de algoritmos"
    echo ""
fi

if [ "$HIGH_ERROR_ENDPOINTS" -gt 0 ]; then
    echo "  $(red '⚠️  Se detectaron') $(red "$HIGH_ERROR_ENDPOINTS") $(red 'endpoints con alta tasa de errores (>5%)')"
    echo "     → Revisar logs de aplicación"
    echo "     → Verificar timeouts y circuit breakers"
    echo ""
fi

if [ "$LOW_THROUGHPUT" -gt 0 ]; then
    echo "  $(yellow '⚠️  Se detectaron') $(yellow "$LOW_THROUGHPUT") $(yellow 'endpoints con bajo throughput (<20 req/s)')"
    echo "     → Considerar escalar pods (horizontal scaling)"
    echo "     → Revisar límites de recursos (CPU/Memory)"
    echo ""
fi

if (( $(echo "$OVERALL_ERROR_RATE < 1" | bc -l) )); then
    echo "  $(green '✅ Sistema estable con baja tasa de errores')"
fi

if [ "$SLOW_ENDPOINTS" -eq 0 ] && [ "$HIGH_ERROR_ENDPOINTS" -eq 0 ]; then
    echo "  $(green '✅ Todos los endpoints tienen buen rendimiento')"
fi

echo ""

# 9. INFORMACIÓN ADICIONAL
echo "$(bold '📁 9. ARCHIVOS GENERADOS')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  📊 Reporte HTML:    $RESULTS_DIR/load_test_report.html"
echo "  📈 Estadísticas:    $RESULTS_DIR/load_test_stats.csv"
echo "  📉 Historial:       $RESULTS_DIR/load_test_stats_history.csv"
echo "  ❌ Errores:         $RESULTS_DIR/load_test_failures.csv"
echo ""
echo "$(bold 'Para ver el reporte completo, abre el archivo HTML en un navegador')"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
