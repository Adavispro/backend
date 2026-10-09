# Backend Microservices - Build & Run Commands

## 1. Build All Services
From the `backend` directory:
```powershell
mvn clean install -U -DskipTests
```

---

## 2. Start Services via `java -jar` (Individually)

Make sure MongoDB (`37017` or `27017`) and Redis (`6379`) are running before starting the services:

### 1. Auth Service (Port 9081)
```powershell
java -jar services/auth-service/target/auth-service-1.0.0-SNAPSHOT.jar
```

### 2. MDM Service (Port 9083)
```powershell
java -jar services/mdm-service/target/mdm-service-1.0.0-SNAPSHOT.jar
```

### 3. IIoT Service (Port 9085)
```powershell
java -jar services/iiot-service/target/iiot-service-1.0.0-SNAPSHOT.jar
```

### 4. License Service (Port 8082)
```powershell
java -jar services/license-service/target/license-service-1.0.0-SNAPSHOT.jar
```

### 5. Audit Service (Port 8084)
```powershell
java -jar services/audit-service/target/audit-service-1.0.0-SNAPSHOT.jar
```

### 6. API Gateway (Port 9080)
```powershell
java -jar services/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
```

---

## 3. Start All Services in Separate PowerShell Windows

To launch all 6 services simultaneously:

```powershell
# From backend directory:
Start-Process powershell -ArgumentList "-NoExit", "-Command", "java -jar services/auth-service/target/auth-service-1.0.0-SNAPSHOT.jar"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "java -jar services/mdm-service/target/mdm-service-1.0.0-SNAPSHOT.jar"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "java -jar services/iiot-service/target/iiot-service-1.0.0-SNAPSHOT.jar"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "java -jar services/license-service/target/license-service-1.0.0-SNAPSHOT.jar"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "java -jar services/audit-service/target/audit-service-1.0.0-SNAPSHOT.jar"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "java -jar services/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar"
```

---

## 4. Alternative: Run via Maven `spring-boot:run` (No JAR required)

```powershell
# Run specific service from backend directory:
mvn -pl services/auth-service spring-boot:run
mvn -pl services/mdm-service spring-boot:run
mvn -pl services/iiot-service spring-boot:run
mvn -pl services/license-service spring-boot:run
mvn -pl services/audit-service spring-boot:run
mvn -pl services/api-gateway spring-boot:run
```

---

## 5. Python Ingestion Services (Standalone)

### API Ingestion Service (MB003, MB004, MB005, MB041):
```powershell
# From backend/api_ingestion_service:
cd api_ingestion_service
python ingest_scheduler.py --continuous --interval 15 --live
```

### File Ingestion Service (MC081 Sejong Compression):
```powershell
# From backend/file_ingestion_service:
cd file_ingestion_service
python ingest_file_scheduler.py
```
