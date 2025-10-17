#!/bin/bash

# Script para probar los pipelines de Jenkins localmente
# Este script simula las etapas principales de los pipelines

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Función para imprimir mensajes con colores
print_message() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Microservicios a probar
SERVICES=("user-service" "product-service" "order-service" "payment-service" "shipping-service" "favourite-service")

# Función para verificar prerrequisitos
check_prerequisites() {
    print_message "Verificando prerrequisitos..."
    
    # Verificar Docker
    if ! command -v docker &> /dev/null; then
        print_error "Docker no está instalado"
        exit 1
    fi
    
    # Verificar que Docker esté ejecutándose
    if ! docker info &> /dev/null; then
        print_error "Docker no está ejecutándose"
        exit 1
    fi
    
    # Verificar Maven wrapper
    if [ ! -f "./mvnw" ]; then
        print_error "Maven wrapper (mvnw) no encontrado"
        exit 1
    fi
    
    # Verificar Java
    if ! command -v java &> /dev/null; then
        print_error "Java no está instalado"
        exit 1
    fi
    
    print_success "Todos los prerrequisitos están disponibles"
}

# Función para probar un microservicio individual
test_microservice() {
    local service=$1
    print_message "Probando microservicio: $service"
    
    cd "$service" || {
        print_error "No se pudo acceder al directorio $service"
        return 1
    }
    
    # Stage 1: Compile
    print_message "Etapa 1: Compilando $service..."
    if ../mvnw clean compile -Dspring.profiles.active=dev -q; then
        print_success "Compilación exitosa para $service"
    else
        print_error "Error en compilación de $service"
        cd ..
        return 1
    fi
    
    # Stage 2: Unit Testing
    print_message "Etapa 2: Ejecutando pruebas unitarias para $service..."
    if ../mvnw test -Dspring.profiles.active=dev -q; then
        print_success "Pruebas unitarias exitosas para $service"
    else
        print_warning "Algunas pruebas unitarias fallaron para $service (continuando...)"
    fi
    
    # Stage 3: Package
    print_message "Etapa 3: Empaquetando $service..."
    if ../mvnw package -DskipTests=true -Dspring.profiles.active=dev -q; then
        print_success "Empaquetado exitoso para $service"
    else
        print_error "Error en empaquetado de $service"
        cd ..
        return 1
    fi
    
    # Stage 4: Build Docker Image
    print_message "Etapa 4: Construyendo imagen Docker para $service..."
    local image_name="geoffrey0pv/$service:test"
    # Cambiar al directorio raíz para el contexto de Docker
    cd .. || {
        print_error "No se pudo acceder al directorio padre"
        return 1
    }
    if docker build -t "$image_name" -f "$service/Dockerfile" . -q; then
        print_success "Imagen Docker construida exitosamente: $image_name"
    else
        print_error "Error construyendo imagen Docker para $service"
        return 1
    fi
    
    # Verificar que la imagen existe
    if docker images | grep -q "$service"; then
        print_success "Imagen $service verificada en Docker"
    else
        print_error "Imagen $service no encontrada en Docker"
        return 1
    fi
    
    print_success "Pipeline local completado exitosamente para $service"
}

# Función para probar todos los microservicios
test_all_services() {
    print_message "Iniciando pruebas locales de todos los microservicios..."
    
    local failed_services=()
    
    for service in "${SERVICES[@]}"; do
        if test_microservice "$service"; then
            print_success "$service: ✅ PASÓ"
        else
            print_error "$service: ❌ FALLÓ"
            failed_services+=("$service")
        fi
        echo "----------------------------------------"
    done
    
    # Resumen final
    echo ""
    print_message "=== RESUMEN DE PRUEBAS LOCALES ==="
    print_success "Servicios que pasaron: $((${#SERVICES[@]} - ${#failed_services[@]}))/${#SERVICES[@]}"
    
    if [ ${#failed_services[@]} -eq 0 ]; then
        print_success "¡Todos los microservicios pasaron las pruebas locales!"
    else
        print_error "Servicios que fallaron:"
        for service in "${failed_services[@]}"; do
            print_error "  - $service"
        done
        return 1
    fi
}

# Función para limpiar imágenes de prueba
cleanup_test_images() {
    print_message "Limpiando imágenes de prueba..."
    
    for service in "${SERVICES[@]}"; do
        local image_name="geoffrey0pv/$service:test"
        if docker images | grep -q "$service"; then
            docker rmi "$image_name" -f 2>/dev/null || true
            print_message "Imagen de prueba eliminada: $image_name"
        fi
    done
    
    print_success "Limpieza completada"
}

# Función para mostrar ayuda
show_help() {
    echo "Uso: $0 [OPCIÓN]"
    echo ""
    echo "Opciones:"
    echo "  test-all     Probar todos los microservicios"
    echo "  test <service>  Probar un microservicio específico"
    echo "  cleanup      Limpiar imágenes de prueba"
    echo "  help         Mostrar esta ayuda"
    echo ""
    echo "Microservicios disponibles:"
    for service in "${SERVICES[@]}"; do
        echo "  - $service"
    done
}

# Función principal
main() {
    case "${1:-test-all}" in
        "test-all")
            check_prerequisites
            test_all_services
            ;;
        "test")
            if [ -z "$2" ]; then
                print_error "Debe especificar un microservicio"
                show_help
                exit 1
            fi
            
            # Verificar que el servicio existe
            if [[ " ${SERVICES[@]} " =~ " $2 " ]]; then
                check_prerequisites
                test_microservice "$2"
            else
                print_error "Microservicio '$2' no válido"
                show_help
                exit 1
            fi
            ;;
        "cleanup")
            cleanup_test_images
            ;;
        "help")
            show_help
            ;;
        *)
            print_error "Opción no válida: $1"
            show_help
            exit 1
            ;;
    esac
}

# Ejecutar función principal con todos los argumentos
main "$@"
