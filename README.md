# Student Database

## 📘 Overview

The **Student Database** (name still under discussion) is a Java-based application designed to manage and store student information efficiently.
It allows admins to perform CRUD (Create, Read, Update, Delete) operations on student records, classes, subjects, and other school-related data, making it a valuable tool for educational institutions.
Students can view their progress, and teachers can assign them topics, based on subjects.

## 🛠️ Features

- Data Management: Add, update, and delete student records and other educational data.
- Data Persistence: Store student information in a structured format (as SQLite database).
- User Interface: Simple command-line interface for interaction for a few commands, web interface for students and teachers.
- Data Validation: Ensure correct data entry through validation checks.

## ⚙️ Technologies Used

- Java: Core programming language.
- Gradle: Build automation tool.
- JUnit: Testing framework for unit tests.
- JDBC: Java Database Connectivity for data storage.

## 🚀 Getting Started

To get a copy of the project up and running on your local machine for development and testing purposes, follow these steps:

### 🏗️ Building from source

“This section describes how to build the project using Gradle.”

#### 🥖 Prerequisites

Ensure you have the following installed:

- [Java 17 or higher](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html)
- [Git](https://git-scm.com/)


#### 🧙 Installation

1. Clone the repository:
    ```bash
    git clone https://github.com/Schlaumeier5/student-database.git
    cd student-database
    ```
2. Build the project using Gradle:
    ```bash
    ./gradlew build
    ```
3. You can find the jar file under `build/libs/student-data-base-(version)-fat.jar`. Be sure to copy the "fat" one because it has all dependencies packed in one jar.
4. Create a keystore in the directory you want to run the program in
     This is needed for https. If you have a jdk installed, you can just run:
     ```bash
     keytool -genkey -keyalg RSA -keystore keys/web/keystore.jks -storetype JKS
     ```

### ⬇️ Download latest release

#### 🥖 Prerequisites

Ensure you have the following installed:

- [Java 17 or higher](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html)

#### 🧙 Installation

1. Download the fat jar from the latest release
2. Move it in the folder you want the server files in
3. Run the jar using
       ```bash
       java -jar [name of the jar].jar
       ```

You can also add command line arguments (see below), or create a shell script.

## 📄 Usage

You can run the application over command line:
```bash
java -jar [the jar received from installation]
```
You can run different commands afterwards, the command "help" prints a list of all available commands.

### 💻 Command line arguments

There are a few command line arguments you can give the program:

- `--suppress-cmd [true|false]`
    Does not use the command line input, but the command line arguments instead. Also does not enable commands. Useful for background tasks.
    Default value: `false` if not specified, `true` if specified, but no value is given
- `--web-server [true|false]`
    Determines if the web server should be started. Useful for only modifying the database.
    Default value: `true`
- `--database (database path)`
    Specifies the path to the internal SQLite database.
    Default value: `database`
- `--keystore (keystore path)`
    Specifies the path to the keystore
- `--keystore-password (keystore pass)`
    The password you entered when generating the keystore.

### 🌐 Web interface

The most important part of the program is the web interface, accessible over a browser using https.

# 🧪 Running Tests

To run the unit tests included in the project (after you cloned this project):
```bash
./gradlew test
```
This will execute all tests and display the results in the terminal. Additionally, it will give you a pretty html output if the test fails.

# 📸 Screenshots

TODO: Create screenshots

# 🔄 Contributing

Contributions are welcome! If you have suggestions, improvements, or bug fixes, please fork the repository and submit a pull request. For major changes, open an issue first to discuss what you would like to change.

# 📄 License
TODO: Add license
