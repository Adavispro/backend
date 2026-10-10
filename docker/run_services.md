# Backend Java Microservices Control Guide

This guide provides one-line PowerShell commands to **Build**, **Start**, **Stop**, and **Check Status** for all 6 backend Java microservices:

| Service | Port | Target JAR Path |
| :--- | :--- | :--- |
| **API Gateway** | `9080` | `services/api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar` |
| **Auth Service** | `9081` | `services/auth-service/target/auth-service-1.0.0-SNAPSHOT.jar` |
| **MDM Service** | `9083` | `services/mdm-service/target/mdm-service-1.0.0-SNAPSHOT.jar` |
| **IIoT Service** | `9085` | `services/iiot-service/target/iiot-service-1.0.0-SNAPSHOT.jar` |
| **License Service** | `8082` | `services/license-service/target/license-service-1.0.0-SNAPSHOT.jar` |
| **Audit Service** | `8084` | `services/audit-service/target/audit-service-1.0.0-SNAPSHOT.jar` |

---

## 1. Build All Services (Pre-requisite)

Before running the JAR files for the first time or after code changes:

```powershell
# From backend directory:
mvn clean install -U -DskipTests
```

---

## 2. Start All Java Services (Single Line)

### From Workspace Root (`Adavis_Workspace`):
```powershell
@("auth-service","mdm-service","iiot-service","license-service","audit-service","api-gateway") | ForEach-Object { Start-Process powershell -ArgumentList "-NoExit","-Command","cd backend; java -jar services/$_/target/$_-1.0.0-SNAPSHOT.jar" }
```

### From `backend` Directory:
```powershell
@("auth-service","mdm-service","iiot-service","license-service","audit-service","api-gateway") | ForEach-Object { Start-Process powershell -ArgumentList "-NoExit","-Command","java -jar services/$_/target/$_-1.0.0-SNAPSHOT.jar" }
```

---

## 3. Stop All Java Services (Single Line)

### Option A: Quick Stop (Kill all Java processes)
```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
```

### Option B: Targeted Stop (Kill only processes listening on backend service ports)
```powershell
9080,9081,9083,9085,8082,8084 | ForEach-Object { (Get-NetTCPConnection -LocalPort $_ -ErrorAction SilentlyContinue).OwningProcess } | Where-Object { $_ } | Select-Object -Unique | ForEach-Object { Stop-Process -Id $_ -Force }
```

---

## 4. Check Status / Health of Services

To verify which services are currently running and listening on their ports:

```powershell
9080,9081,9083,9085,8082,8084 | ForEach-Object { $port = $_; $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue; [PSCustomObject]@{ Port = $port; Status = if ($conn) { "RUNNING (PID $($conn.OwningProcess[0]))" } else { "STOPPED" } } } | Format-Table -AutoSize
```
