plugins{id("com.android.application");id("org.jetbrains.kotlin.android")}
android{
 namespace="com.neo.wakeup"
 compileSdk=34
 defaultConfig{applicationId="com.neo.wakeup";minSdk=26;targetSdk=34;versionCode=1;versionName="1.0"}
 buildFeatures{buildConfig=true}
 flavorDimensions += "role"
 productFlavors{
  create("personal"){dimension="role";buildConfigField("boolean","MACRO","false")}
  create("macro"){dimension="role";applicationIdSuffix=".macro";buildConfigField("boolean","MACRO","true")}
 }
 compileOptions{sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17}
 kotlinOptions{jvmTarget="17"}
}
