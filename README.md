[![obsolete JetBrains project](http://jb.gg/badges/obsolete.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

IntelliJ Visual SourceSafe Integration
==

The plugin provides IntelliJ integration with <a href="http://msdn.microsoft.com/en-us/library/3h0544kx(v=vs.80).aspx">Microsoft Visual SourceSafe.</a>

Visual SourceSafe is a file-level version control system. This plugin allows using it from within the product, making even refactoring consequences transparent for the user.

The following features are available:
* Dedicated page under the Version Control node in the Settings/Preferences dialog
* Implementing the most frequently needed commands (Open Source Safe Explorer, Check In/Out, Add, Undo Checkout, Get Latest Version) 
* Next, Previous, Rollback, Old text actions are available from the dedicated gutter bar in changed locations.

###To build and run the plugin:
1. Clone the project and open in IDEA (tfsintegration.iml should be used)
2. Configure IntelliJ Platform Plugin SDK called **IntelliJ IDEA SDK** pointing to the existing IDEA installation using Project Settings
3. Run using provided **Plugin** run configuration
4. After applying hte needed changes use *Build - Prepare Plugin Module for deployment* to generate the jar
5. Load the jar using *Settings/Preferences - Plugins*
