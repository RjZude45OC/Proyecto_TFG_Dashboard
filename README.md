# Docker & *Arr Stack Monitoring Dashboard

A comprehensive monitoring dashboard application for Docker containers and *arr stack (Sonarr, Radarr, Lidarr, etc.) developed as a final project for Higher Degree in Multi-platform Application Development at IES Doctor Balmis.


## 🚀 Features

- **Real-time Docker Container Monitoring**
  - Container status and health checks
  - Resource usage metrics (CPU, Memory, Network)
  - Container logs viewer
  - Start/Stop/Restart container controls

- ***Arr Stack Integration**
  - Monitor Sonarr and other *arr applications
  - Queue status and download progress
  - Library statistics
  - Upcoming downloads
  - System status and disk space monitoring

- **Dashboard Features**
  - Real-time updates
  - Dark/Light theme support
  - responsive design
  - Alert notifications

## 🛠️ Technologies

- **Backend**
  - Java
  - Spring Boot
  - Docker API
  - WebSocket for real-time updates

- **Frontend**
  - JavaFx
  - CSS

## 📋 Prerequisites

- Java JDK 17 or higher
- Docker Engine
- Docker API access
- *Arr applications (if using the *arr stack features)

## 🔧 Installation

1. Clone the repository:
```bash
git clone https://github.com/RjZude45OC/Proyecto_TFG_Dashboard.git
```

2. Configure application settings:
```bash
cd Proyecto_TFG_Dashboard
cp config.example.yml config.yml
# Edit config.yml with your settings
```

3. Build the application:
```bash
./mvnw clean package
```

4. Run the application:
```bash
java --module-path lib --add-modules javafx.controls,javafx.fxml,javafx.web,javafx.swing -cp "Dashboard_TFG-1.0-SNAPSHOT.jar;lib/*" com.tfg.dashboard_tfg.App
```

## ⚙️ Configuration

### Docker Configuration
```yaml
docker:
  host: unix:///var/run/docker.sock
  api-version: 1.41
```

### *Arr Stack Configuration
```yaml
arr-stack:
  sonarr:
    url: http://localhost:8989
    apikey: your-api-key
  prowlarr:
    url: http://localhost:9696
    apikey: your-api-key
  jellyfin:
    url: http://localhost:8096
    apikey: your-api-key
```

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👤 Author

- **[RjZude45OC](https://github.com/RjZude45OC)**
- Project developed as final thesis for DAM at IES Doctor Balmis

## 🙏 Acknowledgments

- IES Doctor Balmis faculty and staff
- Docker community
- *Arr stack developers
- Open source community

## 📈 Project Status

Current Version: 1.0.0
Status: Active Development
