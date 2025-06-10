# Docker & *Arr Stack Monitoring Dashboard

A comprehensive monitoring dashboard application for Docker containers and *arr stack (Sonarr, Radarr, Lidarr, etc.) developed as a final project for Higher Degree in Multi-platform Application Development at IES Doctor Balmis.

![Dashboard Preview](screenshots/dashboard.png)

## 🚀 Features

- **Real-time Docker Container Monitoring**
  - Container status and health checks
  - Resource usage metrics (CPU, Memory, Network)
  - Container logs viewer
  - Start/Stop/Restart container controls

- ***Arr Stack Integration**
  - Monitor Sonarr, Radarr, and other *arr applications
  - Queue status and download progress
  - Library statistics
  - Upcoming downloads
  - System status and disk space monitoring

- **Dashboard Features**
  - Customizable widgets
  - Real-time updates
  - Dark/Light theme support
  - Mobile-responsive design
  - Alert notifications

## 🛠️ Technologies

- **Backend**
  - Java
  - Spring Boot
  - Docker API
  - WebSocket for real-time updates

- **Frontend**
  - HTML5
  - CSS3
  - JavaScript
  - Bootstrap for responsive design

## 📋 Prerequisites

- Java JDK 11 or higher
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
java -jar target/dashboard.jar
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
  radarr:
    url: http://localhost:7878
    apikey: your-api-key
```

## 🖥️ Usage

1. Access the dashboard at `http://localhost:8080`
2. Log in with your credentials
3. Configure your monitoring preferences
4. Add widgets to your dashboard

## 🔐 Security

- API key authentication for *arr services
- HTTPS support
- Docker socket security considerations
- Role-based access control

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

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
