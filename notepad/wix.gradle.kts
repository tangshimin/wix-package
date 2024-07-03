import java.io.*
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.OutputKeys
import java.nio.charset.StandardCharsets


val licenseFile = project.file("license.rtf")
val licensePath = if(licenseFile.exists()) licenseFile.absolutePath else ""

val iconFile = project.file("src/main/resources/logo/logo.ico")
val iconPath = if(iconFile.exists()) iconFile.absolutePath else ""


// 可以设置为开发者的名字或开发商的名字，在控制面板里 manufacturer 会显示为发布者
// 这个信息会和项目的名称一起写入到注册表中
val manufacturer = "未知"

// 快捷方式的名字，会显示在桌面和开始菜单
val shortcutName = "记事本"

val appDir = project.layout.projectDirectory.dir("build/compose/binaries/main/app/")

project.tasks.register<Exec>("harvest") {
    group = "compose wix"
    description = "Generates WiX authoring from application image"
    val createDistributable = tasks.named("createDistributable")
    dependsOn(createDistributable)
    workingDir(appDir)
    val heat = project.layout.projectDirectory.file("build/wix311/heat.exe").getAsFile().absolutePath

    commandLine(
        heat,
        "dir",
        "./${project.name}",
        "-nologo",
        "-cg",
        "DefaultFeature",
        "-gg",
        "-sfrag",
        "-sreg",
        "-template",
        "product",
        "-out",
        "${project.name}.wxs",
        "-var",
        "var.SourceDir"
    )

}

project.tasks.register("editWxs") {
    group = "compose wix"
    description = "Edit the WXS File"
    val harvest = tasks.named("harvest")
    dependsOn(harvest)
    doLast {
        editWixTask(
            shortcutName = shortcutName,
            iconPath = iconPath,
            licensePath = licensePath,
            manufacturer = manufacturer
        )
    }
}

project.tasks.register<Exec>("compileWxs") {
    group = "compose wix"
    description = "Compile WXS file to WIXOBJ"
    val editWxs = tasks.named("editWxs")
    dependsOn(editWxs)
    workingDir(appDir)
    val candle = project.layout.projectDirectory.file("build/wix311/candle.exe").getAsFile().absolutePath
    commandLine(candle, "${project.name}.wxs","-nologo", "-dSourceDir=.\\${project.name}")
}

project.tasks.register<Exec>("lightWixobj") {
    group = "compose wix"
    description = "Linking the .wixobj file and creating a MSI"
    val compileWxs = tasks.named("compileWxs")
    dependsOn(compileWxs)
    workingDir(appDir)
    val light = project.layout.projectDirectory.file("build/wix311/light.exe").getAsFile().absolutePath

    commandLine(light, "-ext", "WixUIExtension", "-cultures:zh-CN", "-spdb","-nologo", "${project.name}.wixobj", "-o", "${project.name}-${project.version}.msi")
}




private fun editWixTask(
    shortcutName: String,
    iconPath: String,
    licensePath: String,
    manufacturer:String
) {
    val wixFile = project.layout.projectDirectory.dir("build/compose/binaries/main/app/${project.name}.wxs").getAsFile()

    val dbf = DocumentBuilderFactory.newInstance()
    val doc = dbf.newDocumentBuilder().parse(wixFile)
    doc.documentElement.normalize()

    // 设置 Product 节点
    //<Product Codepage="" Id="" Language="" Manufacturer="" Name="" UpgradeCode="" Version="1.0">
    val productElement = doc.documentElement.getElementsByTagName("Product").item(0) as Element
    productElement.setAttribute("Manufacturer", manufacturer)
    productElement.setAttribute("Codepage", "936")
    // 这个 Name 属性会出现在安装引导界面
    // 控制面板-程序列表里也是这个名字
    productElement.setAttribute("Name", "${shortcutName}")
    productElement.setAttribute("Version", "${project.version}")

    // 设置升级码, 用于升级,大版本更新时，可能需要修改这个值
    // 如果要修改这个值，可能还需要修改安装位置，如果不修改安装位置，两个版本会安装在同一个位置
    // 这段代码和 MajorUpgrade 相关，如果 UpgradeCode 一直保持不变，安装新版的时候会自动卸载旧版本。
    val upgradeCode = createNameUUID("UpgradeCode")
    productElement.setAttribute("UpgradeCode", upgradeCode)


    // 设置 Package 节点
    // <Package Compressed="" InstallerVersion="" Languages="" Manufacturer="" Platform="x64"/>
    val packageElement = productElement.getElementsByTagName("Package").item(0) as Element
    packageElement.setAttribute("Compressed", "yes")
    packageElement.setAttribute("InstallerVersion", "200")
    packageElement.setAttribute("Languages", "1033")
    packageElement.setAttribute("Manufacturer", manufacturer)
    packageElement.setAttribute("Platform", "x64")


    //  <Directory Id="TARGETDIR" Name="SourceDir">
    val targetDirectory = doc.documentElement.getElementsByTagName("Directory").item(0) as Element

    // 添加桌面快捷方式
    val desktopFolderElement = directoryBuilder(doc, id = "DesktopFolder")
    val desktopGuid = createNameUUID("DesktopFolder")
    val shortcutComponentElement = componentBuilder(doc, id = "DesktopFolder", guid = desktopGuid)
    val regComponentElement = registryBuilder(doc, id = "DesktopShortcutReg", productCode = "[ProductCode]")
    val shortcutElement = shortcutBuilder(
        doc,
        id = "DesktopShortcut",
        directory = "DesktopFolder",
        workingDirectory = "INSTALLDIR",
        name = shortcutName,
        target = "[INSTALLDIR]${project.name}.exe"
    )
    shortcutComponentElement.appendChild(regComponentElement)
    shortcutComponentElement.appendChild(shortcutElement)
    desktopFolderElement.appendChild(shortcutComponentElement)
    targetDirectory.appendChild(desktopFolderElement)

    // 添加开始菜单快捷方式
    val programMenuFolderElement = directoryBuilder(doc, id = "ProgramMenuFolder", name = "Programs")
    // 开始菜单的文件夹名称，一般是软件的名称或开发商的名称
    val programeMenuDir = directoryBuilder(doc, id = "ProgramMenuDir", name = "$shortcutName")
    val menuGuid = createNameUUID("programeMenuDirComponent")
    val programeMenuDirComponent = componentBuilder(doc, id = "programeMenuDirComponent", guid = menuGuid)
    val startMenuShortcut = shortcutBuilder(
        doc,
        id = "startMenuShortcut",
        directory = "ProgramMenuDir",
        workingDirectory = "INSTALLDIR",
        name = shortcutName,
        target = "[INSTALLDIR]${project.name}.exe"
    )
    val removeFolder = removeFolderBuilder(doc, id = "ProgramMenuDir")
    val pRegistryValue = registryBuilder(doc, id = "ProgramMenuShortcutReg", productCode = "[ProductCode]")

    programMenuFolderElement.appendChild(programeMenuDir)
    programeMenuDir.appendChild(programeMenuDirComponent)
    programeMenuDirComponent.appendChild(startMenuShortcut)
    programeMenuDirComponent.appendChild(removeFolder)
    programeMenuDirComponent.appendChild(pRegistryValue)

    // 添加卸载软件的快捷方式
    val uninstallGuid = createNameUUID("UninstallProduct")
    val uninstallComponent = componentBuilder(doc, id = "UninstallProduct", guid = uninstallGuid)
    val uninstallShortcut = shortcutBuilder(
        doc,
        id = "uninstallShortcut",
        name = "卸载${shortcutName}",
        directory = "ProgramMenuDir",
        target = "[System64Folder]msiexec.exe",
        arguments = "/x [ProductCode]"
    )
    val uninstallRegistry = registryBuilder(doc, id = "uninstallShortcutReg", productCode = "[ProductCode]")
    uninstallComponent.appendChild(uninstallShortcut)
    uninstallComponent.appendChild(uninstallRegistry)

    programeMenuDir.appendChild(uninstallComponent)
//    programMenuFolderElement.appendChild(uninstallComponent)

    targetDirectory.appendChild(programMenuFolderElement)

    // 设置所有组件的架构为 64 位
    val components = doc.documentElement.getElementsByTagName("Component")
    for (i in 0 until components.length) {
        val component = components.item(i) as Element
        val win64 = doc.createAttribute("Win64")
        win64.value = "yes"
        component.setAttributeNode(win64)
    }

    // 添加 ProgramFiles64Folder 节点
    val programFilesElement = doc.createElement("Directory")
    val idAttr = doc.createAttribute("Id")
    idAttr.value = "ProgramFiles64Folder"
    programFilesElement.setAttributeNode(idAttr)
    targetDirectory.appendChild(programFilesElement)
    val installDir = targetDirectory.getElementsByTagName("Directory").item(0)
    // 移除 installDir 节点
    val removedNode = targetDirectory.removeChild(installDir)
    // 将 installDir 节点添加到 programFilesElement 节点
    programFilesElement.appendChild(removedNode)
    // 设置安装目录的 Id 为 INSTALLDIR，快捷方式需要引用这个 Id
    val installDirElement = programFilesElement.getElementsByTagName("Directory").item(0) as Element
    installDirElement.setAttribute("Id", "INSTALLDIR")


    // 设置 Feature 节点
    val featureElement = doc.getElementsByTagName("Feature").item(0) as Element
    featureElement.setAttribute("Id", "Complete")
    featureElement.setAttribute("Title", "${project.name}")

    // 设置 UI
    // 添加 <Property Id="WIXUI_INSTALLDIR" Value="INSTALLDIR" />
    val installUI = doc.createElement("Property")
    val propertyId = doc.createAttribute("Id")
    propertyId.value = "WIXUI_INSTALLDIR"
    val peopertyValue = doc.createAttribute("Value")
    peopertyValue.value = "INSTALLDIR"
    installUI.setAttributeNode(propertyId)
    installUI.setAttributeNode(peopertyValue)
    productElement.appendChild(installUI)

    // 添加 <UIRef Id="WixUI_InstallDir" />
    val installDirUIRef = doc.createElement("UIRef")
    val dirUiId = doc.createAttribute("Id")
    dirUiId.value = "WixUI_InstallDir"
    installDirUIRef.setAttributeNode(dirUiId)
    productElement.appendChild(installDirUIRef)

    // 添加 <UIRef Id="WixUI_ErrorProgressText" />
    val errText = doc.createElement("UIRef")
    val errUiId = doc.createAttribute("Id")
    errUiId.value = "WixUI_ErrorProgressText"
    errText.setAttributeNode(errUiId)
    productElement.appendChild(errText)

    //  添加 Icon, 这个 Icon 会显示在控制面板的应用程序列表
    //  <Icon Id="icon.ico" SourceFile="$iconPath"/>
    //  <Property Id="ARPPRODUCTICON" Value="icon.ico" />
    if(iconPath.isNotEmpty()) {
        val iconElement = doc.createElement("Icon")
        val iconId = doc.createAttribute("Id")
        iconId.value = "icon.ico"
        val iconSourceF = doc.createAttribute("SourceFile")
        iconSourceF.value = iconPath
        iconElement.setAttributeNode(iconId)
        iconElement.setAttributeNode(iconSourceF)

        val iconProperty = doc.createElement("Property")
        val iconPropertyId = doc.createAttribute("Id")
        iconPropertyId.value = "ARPPRODUCTICON"
        val iconPropertyValue = doc.createAttribute("Value")
        iconPropertyValue.value = "icon.ico"
        iconProperty.setAttributeNode(iconPropertyId)
        iconProperty.setAttributeNode(iconPropertyValue)

        productElement.appendChild(iconElement)
        productElement.appendChild(iconProperty)
    }



    // 设置 license file
    //  <WixVariable Id="WixUILicenseRtf" Value="license.rtf" />
    if (licensePath.isNotEmpty()) {
        val wixVariable = doc.createElement("WixVariable")
        val wixVariableId = doc.createAttribute("Id")
        wixVariableId.value = "WixUILicenseRtf"
        val wixVariableValue = doc.createAttribute("Value")
        wixVariableValue.value = licensePath
        wixVariable.setAttributeNode(wixVariableId)
        wixVariable.setAttributeNode(wixVariableValue)
        productElement.appendChild(wixVariable)
    }


    // 安装新版时，自动卸载旧版本，已经安装新版，再安装旧版本，提示用户先卸载新版。
    // 这段逻辑要和 UpgradeCode 一起设置，如果 UpgradeCode 一直保持不变，安装新版的时候会自动卸载旧版本。
    // 如果 UpgradeCode 改变了，可能会安装两个版本
    // <MajorUpgrade AllowSameVersionUpgrades="yes" DowngradeErrorMessage="A newer version of [ProductName] is already installed." AllowSameVersionUpgrades="yes"/>
    val majorUpgrade = doc.createElement("MajorUpgrade")
    val majorUpgradeDowngradeErrorMessage = doc.createAttribute("DowngradeErrorMessage")
    majorUpgradeDowngradeErrorMessage.value = "新版的[ProductName]已经安装，如果要安装旧版本，请先把新版本卸载。"
    val majorUpgradeAllowSameVersionUpgrades = doc.createAttribute("AllowSameVersionUpgrades")
    majorUpgradeAllowSameVersionUpgrades.value = "yes"
    majorUpgrade.setAttributeNode(majorUpgradeAllowSameVersionUpgrades)
    majorUpgrade.setAttributeNode(majorUpgradeDowngradeErrorMessage)
    productElement.appendChild(majorUpgrade)


    // 设置 fragment 节点
    val fragmentElement = doc.getElementsByTagName("Fragment").item(0) as Element
    val componentGroup = fragmentElement.getElementsByTagName("ComponentGroup").item(0) as Element
    val desktopFolderRef = componentRefBuilder(doc, "DesktopFolder")
    val programMenuDirRef = componentRefBuilder(doc, "programeMenuDirComponent")
    val uninstallProductRef = componentRefBuilder(doc, "UninstallProduct")
    componentGroup.appendChild(desktopFolderRef)
    componentGroup.appendChild(programMenuDirRef)
    componentGroup.appendChild(uninstallProductRef)

    generateXml(doc, wixFile)
}


private fun generateXml(doc: Document, file: File) {

    // Instantiate the Transformer
    val transformerFactory = TransformerFactory.newInstance()
    transformerFactory.setAttribute("indent-number", 4);
    val transformer = transformerFactory.newTransformer()

    // Enable indentation and set encoding
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")

    val source = DOMSource(doc)
    val result = StreamResult(file)
    transformer.transform(source, result)
}


private fun directoryBuilder(doc: Document, id: String, name: String = ""): Element {
    val directory = doc.createElement("Directory")
    val attrId = doc.createAttribute("Id")
    attrId.value = id
    directory.setAttributeNode(attrId)
    if (name.isNotEmpty()) {
        val attrName = doc.createAttribute("Name")
        attrName.value = name
        directory.setAttributeNode(attrName)
    }
    return directory
}

private fun componentBuilder(doc: Document, id: String, guid: String): Element {
    val component = doc.createElement("Component")
    val scAttrId = doc.createAttribute("Id")
    scAttrId.value = id
    component.setAttributeNode(scAttrId)
    val scGuid = doc.createAttribute("Guid")
    scGuid.value = guid
    component.setAttributeNode(scGuid)
    return component
}

private fun registryBuilder(doc: Document, id: String, productCode: String): Element {
    val regComponentElement = doc.createElement("RegistryValue")
    val regAttrId = doc.createAttribute("Id")
    regAttrId.value = "$id"
    val regAttrRoot = doc.createAttribute("Root")
    regAttrRoot.value = "HKCU"
    val regKey = doc.createAttribute("Key")
    regKey.value = "Software\\${manufacturer}\\${project.name}"
    val regType = doc.createAttribute("Type")
    regType.value = "string"
    val regName = doc.createAttribute("Name")
    regName.value = "ProductCode"
    val regValue = doc.createAttribute("Value")
    regValue.value = productCode
    val regKeyPath = doc.createAttribute("KeyPath")
    regKeyPath.value = "yes"
    regComponentElement.setAttributeNode(regAttrId)
    regComponentElement.setAttributeNode(regAttrRoot)
    regComponentElement.setAttributeNode(regAttrRoot)
    regComponentElement.setAttributeNode(regKey)
    regComponentElement.setAttributeNode(regType)
    regComponentElement.setAttributeNode(regName)
    regComponentElement.setAttributeNode(regValue)
    regComponentElement.setAttributeNode(regKeyPath)
    return regComponentElement
}

private fun shortcutBuilder(
    doc: Document,
    id: String,
    directory: String = "",
    workingDirectory: String = "",
    name: String,
    target: String,
    description: String = "",
    arguments: String = ""
): Element {
    val shortcut = doc.createElement("Shortcut")
    val shortcutId = doc.createAttribute("Id")
    shortcutId.value = id
    val shortcutName = doc.createAttribute("Name")
    shortcutName.value = name
    val shortcutTarget = doc.createAttribute("Target")
    shortcutTarget.value = target
    shortcut.setAttributeNode(shortcutId)

    shortcut.setAttributeNode(shortcutName)
    shortcut.setAttributeNode(shortcutTarget)

    if (directory.isNotEmpty()) {
        val shortcutDir = doc.createAttribute("Directory")
        shortcutDir.value = directory
        shortcut.setAttributeNode(shortcutDir)
    }

    if (workingDirectory.isNotEmpty()) {
        val shortcutWorkDir = doc.createAttribute("WorkingDirectory")
        shortcutWorkDir.value = workingDirectory
        shortcut.setAttributeNode(shortcutWorkDir)
    }
    if (description.isNotEmpty()) {
        val shortcutDescription = doc.createAttribute("Description")
        shortcutDescription.value = description
        shortcut.setAttributeNode(shortcutDescription)
    }

    if (arguments.isNotEmpty()) {
        val shortcutArguments = doc.createAttribute("Arguments")
        shortcutArguments.value = arguments
        shortcut.setAttributeNode(shortcutArguments)
    }

    return shortcut
}

private fun removeFolderBuilder(doc: Document, id: String): Element {
    val removeFolder = doc.createElement("RemoveFolder")
    val attrId = doc.createAttribute("Id")
    attrId.value = id
    removeFolder.setAttributeNode(attrId)
    val attrOn = doc.createAttribute("On")
    attrOn.value = "uninstall"
    removeFolder.setAttributeNode(attrOn)
    return removeFolder
}

private fun componentRefBuilder(doc: Document, id: String): Element {
    val componentRef = doc.createElement("ComponentRef")
    val attrId = doc.createAttribute("Id")
    attrId.value = id
    componentRef.setAttributeNode(attrId)
    return componentRef
}

private fun createNameUUID(str: String): String {
    return "{" + UUID.nameUUIDFromBytes(str.toByteArray(StandardCharsets.UTF_8)).toString().uppercase() + "}"
}
