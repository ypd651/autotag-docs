# AutoTag Docs — Smart PDF Scanner & Semantic Tagging

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21%20%7C%2025-orange.svg)](https://openjdk.org/)
[![Gradle](https://img.shields.io/badge/Gradle-9.7.1-blue.svg)](https://gradle.org/)
[![n8n](https://img.shields.io/badge/n8n-Orchestrator-ea4b71.svg)](https://n8n.io/)
[![Qdrant](https://img.shields.io/badge/Qdrant-Vector%20DB-dc2626.svg)](https://qdrant.tech/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)](https://www.postgresql.org/)

Sistema empresarial integral para la ingesta asíncrona, extracción de texto RAG (*Retrieval-Augmented Generation*), etiquetado semántico automatizado y búsqueda vectorial sobre documentos PDF. 

Diseñado bajo una arquitectura desacoplada basada en eventos (*Event-Driven Triggers*) entre **Spring Boot**, **n8n**, **PostgreSQL** y **Qdrant**.

---

## 👥 Desarrolladores del Proyecto
* **Brayan Fonseca**
* **Yeison Pallares**

---

## 🏛️ Arquitectura del Sistema y Roles

El proyecto se estructuró dividiendo responsabilidades en dos roles de ingeniería:

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                    FRONTEND SPA (HTML5/CSS3/ES6)                        │
│                     Modo Claro/Oscuro • Drag & Drop • Búsqueda Semántica Vectorial       │
└────────────────────────────────────────────┬────────────────────────────────────────────┘
                                             │ HTTP REST
                                             ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                          ROL 1: BACKEND DEVELOPER & DATABASE ADMIN                      │
│                               Spring Boot 25 (Gradle) • Puerto 8080                     │
│  - Controladores REST (/api/documents, /api/search, /api/system/diagnostics)            │
│  - Persistencia JPA/Hibernate con PostgreSQL (app_db en postgres_custom:5432)           │
│  - Despachador de Triggers Webhooks hacia n8n con WebClient asíncrono                   │
└──────────────────────┬──────────────────────────────────────────▲───────────────────────┘
                       │ 1. POST /webhook/process-pdf             │ 4. POST /callback
                       │    (Payload Metadata & FilePath)         │    (Tags & Chunks)
                       ▼                                          │
┌─────────────────────────────────────────────────────────────────┴───────────────────────┐
│                          ROL 2: FRONTEND DEVELOPER & RAG INTEGRATOR                     │
│                                 Orquestador n8n (Puerto 5678)                           │
│  - Webhook Trigger (Response Mode: onReceived)                                          │
│  - Pipeline de Chunking (tamaño: 350 car., overlap: 70 car.) y Tagging Semántico       │
│  - Generador de Embeddings determinísticos (384 dimensiones)                             │
│  - Upsert vectorial en Qdrant (http://qdrant_custom:6333/collections/autotag_documents) │
└─────────────────────────────────────────────┬───────────────────────────────────────────┘
                                              │ Inserción de Vectores
                                              ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                   QDRANT VECTOR DATABASE                                │
│                              qdrant_custom (Puertos 6333 / 6334)                        │
│  - Colección: autotag_documents (384 dim, Distancia Coseno)                             │
│  - Búsqueda por similitud semántica de fragmentos de texto en memoria vectorial         │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## ⚡ Ciclo de Vida de los Triggers (Event-Driven)

1. **Trigger 01 (Upload Multipart):** El usuario sube un PDF desde la interfaz o CLI. Spring Boot almacena el archivo en `./shared-storage/uploads/` y registra el documento en PostgreSQL en estado **`PROCESANDO`**.
2. **Trigger 02 (Webhook Asíncrono a n8n):** Spring Boot despacha un evento `POST http://localhost:5679/webhook/process-pdf`. n8n responde inmediatamente con `HTTP 200 (onReceived)` liberando los recursos del servidor en menos de 20 ms.
3. **Trigger 03 (Extracción, Chunking & Inserción en Qdrant):** n8n procesa el archivo en segundo plano, clasifica las etiquetas contextuales (*Facturación, Finanzas, Legal, Contratos, Tecnología, Arquitectura*), genera los embeddings de 384 dimensiones e inserta los puntos vectoriales en Qdrant.
4. **Trigger 04 (Callback a Spring Boot):** n8n invoca el webhook de retorno `POST http://host.docker.internal:8081/api/documents/{id}/callback`. Spring Boot actualiza el estado a **`LISTO`** y asocia las etiquetas relacionales en PostgreSQL.

---

## 🛠️ Correcciones y Mejoras Aplicadas (Compatibilidad Linux & Windows)

El proyecto cuenta con soporte y optimizaciones nativas tanto para **Linux** como para **Windows**:

1. **Migración al Puerto 8081 (Evita conflicto con Apache Tomcat / IIS):**
   * El puerto estándar `8080` suele estar ocupado por servicios del sistema (como Apache Tomcat en Linux/Ubuntu o IIS en Windows). Se configuró el backend y frontend en el puerto **`8081`** para garantizar disponibilidad inmediata.
2. **Sincronización Total de Puertos Docker & Backend:**
   * Se alineó la configuración de [application.yml](file:///home/camper/Taller-All/autotag-docs/backend/src/main/resources/application.yml) con los mapeos expuestos en [docker-compose.yml](file:///home/camper/Taller-All/autotag-docs/docker-compose.yml):
     * **PostgreSQL:** Puerto host `5433` (`5433:5432`)
     * **Qdrant:** Puerto host `6334` (`6334:6333`)
     * **n8n:** Puerto host `5679` (`5679:5678`)
3. **Soporte Oficial Multiplataforma de Gradle:**
   * Se incluyó el script ejecutable para Linux/macOS [`./gradlew`](file:///home/camper/Taller-All/autotag-docs/backend/gradlew) junto al script de Windows [`gradlew.bat`](file:///home/camper/Taller-All/autotag-docs/backend/gradlew.bat) y la distribución portable `gradle/wrapper/`. Ya no depende de rutas fijas de usuario ni de tener Gradle previamente instalado en el sistema operativo.
4. **Activación del Pipeline en n8n:**
   * El flujo `autotag-pipeline.json` queda registrado y publicado dentro del contenedor `n8n_custom`, registrando el endpoint `/webhook/process-pdf` y el callback hacia `http://host.docker.internal:8081`, asegurando que los archivos subidos pasen de `PROCESANDO` a `LISTO` automáticamente.

---

## 🌐 Hosts, Puertos y Credenciales

| Servicio | Entorno / Contenedor | Host / URL Local | Puerto | Credenciales / Detalles |
| :--- | :--- | :--- | :---: | :--- |
| **Front-End SPA** | Host / Spring Static | [http://localhost:8081](http://localhost:8081) | `8081` | Servido directamente por Spring Boot |
| **Backend REST API** | Host (Spring Boot 25) | [http://localhost:8081/api](http://localhost:8081/api) | `8081` | Spring Boot 3.4.0, Gradle 9.7.1 |
| **Orquestador n8n** | Docker (`n8n_custom`) | [http://localhost:5679](http://localhost:5679) | `5679` | UI del editor de flujos y Webhooks |
| **Qdrant REST API** | Docker (`qdrant_custom`)| [http://localhost:6334](http://localhost:6334) | `6334` | REST API para búsqueda vectorial |
| **Qdrant Dashboard** | Docker (`qdrant_custom`)| [http://localhost:6334/dashboard](http://localhost:6334/dashboard) | `6334` | Panel visual de colecciones y puntos |
| **Qdrant gRPC** | Docker (`qdrant_custom`)| `localhost:6335` | `6335` | Protocolo binario de alta velocidad |
| **PostgreSQL** | Docker (`postgres_custom`)| `localhost:5433` | `5433` | BD: `app_db` \| User: `postgres` \| Pass: `postgres` |
| **Docker Host Bridge** | Red interna Docker | `http://host.docker.internal:8081` | `8081` | Usado por n8n para enviar callbacks a Spring Boot |

---

## 🚀 Guía de Instalación y Puesta en Marcha

### Prerrequisitos
* **Java Development Kit (JDK 21 o superior, compatible con JDK 25)**
* **Docker Desktop / Docker Engine** (con soporte para Linux Containers)

---

### Paso 1: Ubicarse en la Raíz del Proyecto

* **En Linux:**
  ```bash
  cd autotag-docs
  ```
* **En Windows (PowerShell):**
  ```powershell
  cd autotag-docs
  ```

---

### Paso 2: Levantar los Contenedores Docker (PostgreSQL, Qdrant y n8n)

```bash
docker compose up -d
```
Verifica que los tres contenedores estén saludables y activos:
```bash
docker ps
```
Deberás ver: `postgres_custom`, `qdrant_custom` y `n8n_custom`.

---

### Paso 3: Cargar y Publicar el Flujo en n8n

Ejecuta estos comandos para importar y activar el pipeline:

* **En Linux (Bash):**
  ```bash
  docker cp autotag-pipeline.json n8n_custom:/tmp/autotag-pipeline.json
  docker exec n8n_custom n8n import:workflow --input=/tmp/autotag-pipeline.json
  docker exec n8n_custom n8n publish:workflow --id=GZR8oxnRrqKSE43m
  docker restart n8n_custom
  ```

* **En Windows (PowerShell):**
  ```powershell
  docker cp autotag-pipeline.json n8n_custom:/tmp/autotag-pipeline.json
  docker exec n8n_custom n8n import:workflow --input=/tmp/autotag-pipeline.json
  docker exec n8n_custom n8n publish:workflow --id=GZR8oxnRrqKSE43m
  docker restart n8n_custom
  ```

---

### Paso 4: Compilar y Ejecutar el Backend (Spring Boot con Gradle)

Ingresa a la carpeta `backend` y corre el script correspondiente a tu sistema operativo:

* **En Linux / macOS:**
  ```bash
  cd backend
  ./gradlew bootRun
  ```

* **En Windows (PowerShell / CMD):**
  ```powershell
  cd backend
  .\gradlew.bat bootRun
  ```

*El backend iniciará en el puerto **`8081`**, sincronizará PostgreSQL y servirá el Front-End automáticamente.*

---

### Paso 5: Abrir la Aplicación Web

Abre tu navegador e ingresa a:
👉 **[http://localhost:8081](http://localhost:8081)**

---

## 🛠️ Herramienta de Línea de Comandos (CLI Helper)

Para pruebas automatizadas, scripts CI/CD o administración sin navegador, se incluye el script de PowerShell [`cli.ps1`](cli.ps1):

```powershell
# Comprobar salud y conexión de PostgreSQL, Qdrant y n8n
.\cli.ps1 health

# Subir un PDF y disparar el pipeline de n8n
.\cli.ps1 upload .\samples\factura_servicios_cloud_2026.pdf

# Listar todos los documentos indexados con sus etiquetas
.\cli.ps1 list

# Ver el detalle de un documento por su UUID
.\cli.ps1 get <DOCUMENT_UUID>

# Realizar una búsqueda semántica vectorial
.\cli.ps1 search "contratos de confidencialidad y clausulas mercantiles"

# Re-disparar el trigger de n8n para un documento existente
.\cli.ps1 reprocess <DOCUMENT_UUID>
```

---

## 📡 Especificación de la API REST

### 1. Gestión de Documentos
* `POST /api/documents/upload`
  * **Tipo:** `multipart/form-data`
  * **Parámetro:** `file` (Archivo binario PDF)
  * **Respuesta:** JSON con metadatos del documento y estado `PROCESANDO`.
* `GET /api/documents`
  * **Filtros opcionales:** `?status=LISTO` o `?tag=Facturación`
  * **Respuesta:** Lista de documentos con etiquetas relacionales y resumen.
* `GET /api/documents/{id}`
  * **Respuesta:** Detalle completo del documento.
* `POST /api/documents/{id}/reprocess`
  * **Respuesta:** Re-dispara el webhook hacia n8n para re-indexar el PDF.

### 2. Callback de Integración (n8n ➔ Spring Boot)
* `POST /api/documents/{id}/callback`
  * **Cuerpo JSON:**
    ```json
    {
      "documentId": "uuid-aqui",
      "status": "LISTO",
      "tags": ["Facturación", "Finanzas"],
      "summary": "Documento indexado con éxito...",
      "chunksCount": 1,
      "vectorCollection": "autotag_documents"
    }
    ```

### 3. Búsqueda Semántica Vectorial (Spring Boot ➔ Qdrant)
* `GET /api/search?q={consulta}&limit={cantidad}`
  * **Ejemplo:** `GET /api/search?q=servicios+cloud+aws&limit=5`
  * **Respuesta:** Fragmentos de texto (*matchingSnippet*), puntuación de similitud coseno (*score*), etiquetas y nombre de archivo.

### 4. Diagnóstico del Stack
* `GET /api/system/diagnostics`
  * **Respuesta:** Estado en vivo (`UP`/`DOWN`) de PostgreSQL, Qdrant y n8n.

---

## 🎨 Características de la Interfaz Web (Front-End)

1. **Selector Modo Claro / Oscuro:** Botón toggle con persistencia en `localStorage` y paleta de colores SaaS moderna con efectos glassmórficos.
2. **Zona Drag & Drop:** Carga interactiva con barra de progreso animada (*shimmer*).
3. **Métricas en Vivo:** Contadores de documentos totales, procesándose, vectorizados y número de etiquetas únicas.
4. **Filtro de Etiquetas Dinámico:** Chips que permiten filtrar documentos por categorías semánticas al instante.
5. **Modal de Detalle del Documento:** Al hacer clic en cualquier fila de la tabla, se abre un visor con metadatos, resumen RAG, chunks en Qdrant y atajos para búsqueda.
6. **Buscador Semántico Asistido por IA:** Con barras de porcentaje de similitud coseno, snippets textuales y botones de consultas rápidas.
7. **Monitor de Triggers & Terminal de Eventos:** Diagrama visual del flujo y terminal interactiva con marcas de tiempo `[HH:MM:SS]`.

---

## 📂 Estructura de Directorios del Repositorio

```text
autotag-docs/
│
├── backend/                             # Proyecto Spring Boot 25 con Gradle
│   ├── build.gradle                     # Configuración de dependencias (JPA, WebClient, PostgreSQL)
│   ├── settings.gradle                  # Nombre del proyecto Gradle
│   ├── gradlew                          # Wrapper ejecutable de Gradle para Linux / macOS
│   ├── gradlew.bat                      # Wrapper de Gradle para Windows
│   ├── gradle/wrapper/                  # Distribución oficial y portable de Gradle Wrapper
│   └── src/main/java/com/autotag/docs/  # Código fuente Java (Controllers, Services, DTOs, Repositories)
│       └── resources/
│           ├── application.yml          # Configuración de base de datos, n8n y Qdrant
│           └── static/                  # Archivos web servidos directamente por Spring
│
├── frontend/                            # Front-End SPA standalone
│   ├── index.html                       # Marcado semántico, layout y modales
│   ├── styles.css                       # Variables CSS (Light/Dark Mode) y animaciones
│   └── app.js                           # Lógica cliente, auto-polling y llamadas REST
│
├── samples/                             # Archivos PDF reales para pruebas
│   ├── factura_servicios_cloud_2026.pdf
│   ├── contrato_confidencialidad_nda.pdf
│   └── reporte_arquitectura_qdrant_spring.pdf
│
├── shared-storage/                      # Volumen compartido para almacenamiento de PDFs
│   └── uploads/                         # Archivos PDF físicos almacenados
│
├── autotag-pipeline.json                # Definición exportada del flujo de n8n
├── cli.ps1                              # Herramienta CLI de administración en PowerShell
├── docker-compose.yml                   # Orquestación de Postgres, Qdrant y n8n
├── init.sql                             # Script DDL/DML de PostgreSQL (esquema inicial)
└── README.md                            # Documentación técnica del proyecto
```

---

## 📜 Licencia y Uso
Proyecto desarrollado con fines académicos y de demostración técnica para arquitecturas RAG empresariales y orquestación con agentes de Inteligencia Artificial.
