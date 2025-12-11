FROM ubuntu:22.04

# Éviter les prompts interactifs
ENV DEBIAN_FRONTEND=noninteractive

# Installer les dépendances système
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    git \
    zip \
    unzip \
    openjdk-17-jdk \
    wget \
    curl \
    autoconf \
    libtool \
    pkg-config \
    zlib1g-dev \
    libncurses5-dev \
    libncursesw5-dev \
    libtinfo5 \
    cmake \
    libffi-dev \
    libssl-dev \
    && rm -rf /var/lib/apt/lists/*

# Définir Java home
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Installer Android SDK
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/34.0.0

RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    cd $ANDROID_HOME/cmdline-tools && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip && \
    unzip commandlinetools-linux-9477386_latest.zip -d latest && \
    rm commandlinetools-linux-9477386_latest.zip

# Accepter les licences
RUN yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses

# Installer les composants Android
RUN $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platform-tools" \
    "platforms;android-31" \
    "build-tools;34.0.0" \
    "ndk;25.2.9519653"

# Installer Buildozer et dépendances Python
RUN pip3 install --upgrade pip && \
    pip3 install buildozer cython==0.29.33 python-for-android

# Créer le répertoire de travail
WORKDIR /app

# Copier les fichiers du projet
COPY . /app

# Commande par défaut
CMD ["buildozer", "android", "debug"]