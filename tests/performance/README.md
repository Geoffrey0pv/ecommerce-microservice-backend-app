# 🐍 Locust Performance Testing Setup

## 📦 Instalación

```bash
# Instalar Locust y dependencias
pip install locust
pip install requests
pip install faker
pip install numpy
pip install matplotlib
pip install seaborn
```

## 🚀 Ejecución Rápida

```bash
cd tests/performance

# Load Testing básico
locust -f ecommerce_load_test.py --host=http://localhost:8100

# Headless Load Testing
locust -f ecommerce_load_test.py --host=http://localhost:8100 --users 100 --spawn-rate 10 --run-time 10m --headless

# Stress Testing
locust -f ecommerce_stress_test.py --host=http://localhost:8100 --users 500 --spawn-rate 20 --run-time 15m --headless

# Spike Testing  
locust -f ecommerce_spike_test.py --host=http://localhost:8100 --users 300 --spawn-rate 50 --run-time 5m --headless

# Endurance Testing
locust -f ecommerce_endurance_test.py --host=http://localhost:8100 --users 150 --spawn-rate 5 --run-time 1h --headless
```

## 📊 Scripts Disponibles

- **ecommerce_load_test.py** - Load testing estándar (100 usuarios)
- **ecommerce_stress_test.py** - Stress testing (hasta 500 usuarios)  
- **ecommerce_spike_test.py** - Spike testing (picos súbitos)
- **ecommerce_endurance_test.py** - Endurance testing (1 hora)
- **user_behavior.py** - Comportamientos de usuario realistas
- **data_generator.py** - Generación de datos de prueba
- **metrics_analyzer.py** - Análisis avanzado de métricas

## 🎯 Objetivos de Rendimiento

- **👥 Usuarios Concurrentes**: 100-500 usuarios
- **⚡ Requests/segundo**: 50-200 RPS  
- **📊 Response Time**: P95 < 1s, P99 < 2s
- **🎯 Error Rate**: < 1% under normal load
- **💾 Resource Utilization**: CPU < 80%, RAM < 80%

## 📈 Métricas Generadas

- Response time percentiles (50th, 95th, 99th)
- Throughput (RPS) por endpoint
- Error rates y tipos de errores
- Concurrent user behavior
- Resource utilization trends