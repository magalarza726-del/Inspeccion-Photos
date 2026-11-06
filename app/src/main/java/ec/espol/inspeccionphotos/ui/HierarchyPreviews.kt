package ec.espol.inspeccionphotos.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ec.espol.inspeccionphotos.FolderViewMode
import ec.espol.inspeccionphotos.Inspection
import ec.espol.inspeccionphotos.PhotoItem

private val previewTree = listOf(
    Inspection(
        id = "sept",
        name = "Septiembre 2026",
        created = "2026-09-08",
        ot = "7899",
        viewMode = FolderViewMode.LIST,
        children = listOf(
            Inspection(
                id = "cliente-a",
                name = "Cliente A",
                created = "2026-09-08",
                viewMode = FolderViewMode.LIST,
                children = listOf(
                    Inspection(
                        id = "planta-1",
                        name = "Planta 1",
                        created = "2026-09-08",
                        viewMode = FolderViewMode.GALLERY,
                        photos = listOf(
                            PhotoItem("IMG_001.jpg", "/preview/IMG_001.jpg"),
                            PhotoItem("IMG_002.jpg", "/preview/IMG_002.jpg"),
                            PhotoItem("IMG_003.jpg", "/preview/IMG_003.jpg"),
                            PhotoItem("IMG_004.jpg", "/preview/IMG_004.jpg")
                        ),
                        children = listOf(
                            Inspection(id = "linea-a", name = "Línea A", created = "2026-09-08")
                        )
                    )
                )
            )
        )
    ),
    Inspection(id = "cliente-b", name = "Cliente B", created = "2026-09-08", viewMode = FolderViewMode.LIST),
    Inspection(id = "mant", name = "Mantenimiento", created = "2026-09-08", viewMode = FolderViewMode.LIST)
)

@Preview(name = "V10 · Carpetas · teléfono", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun HierarchyRootPhonePreview() {
    val p = legacyPalette(true)
    InspeccionPhotosTheme {
        LegacyAppShell(
            palette = p,
            selected = LegacyRootSection.FOLDERS,
            compactTitle = "Carpetas",
            compactSubtitle = "Gestiona tu estructura de carpetas",
            onHome = {}
        ) { compact ->
            HierarchyRootScreen(
                folders = previewTree,
                palette = p,
                compact = compact,
                rootViewMode = FolderViewMode.LIST,
                showOt = true,
                onRootViewMode = {}, onOpen = {}, onNewFolder = {}, onEditOt = {}, onRename = {},
                onDuplicate = {}, onCloneStructure = {}, onConfigure = {}, onToggleCollapsed = {}, onSetAllCollapsed = {},
                onMoveFolders = { _, _ -> }, onExport = {}, onDelete = {}
            )
        }
    }
}

@Preview(name = "V10 · Fotos · galería", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun HierarchyFolderGalleryPreview() {
    val p = legacyPalette(true)
    val folder = previewTree.first().children.first().children.first()
    InspeccionPhotosTheme {
        LegacyAppShell(
            palette = p,
            selected = LegacyRootSection.FOLDERS,
            compactTitle = folder.name,
            compactSubtitle = "${folder.children.size} subcarpetas · ${folder.photos.size} fotos",
            onHome = {}
        ) { compact ->
            HierarchyFolderScreen(
                folder = folder,
                ancestors = listOf(previewTree.first(), previewTree.first().children.first()),
                depth = 2,
                allRoots = previewTree,
                palette = p,
                compact = compact,
                showOt = true,
                onBack = {}, onSaveOt = {}, onEditGeneralObservation = {}, onAddChild = {}, onConfigureFolder = {},
                onSetViewMode = {}, onOpenChild = {}, onChildMenu = { _, _ -> }, onToggleChildCollapsed = {},
                onSetAllChildrenCollapsed = {}, onMoveFolders = { _, _ -> }, onAddPhoto = {}, onOpenPhoto = {},
                onRenamePhoto = {}, onEditPhoto = {}, onEditPhotoObservation = {}, onDownloadPhoto = {}, onDeletePhoto = {}, onDuplicateFolder = {}, onCloneStructure = {},
                onEditReportTable = {}, onEditMainImage = {}, onExport = {}
            )
        }
    }
}

@Preview(name = "V10 · Carpetas · tablet", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun HierarchyRootTabletPreview() {
    val p = legacyPalette(true)
    InspeccionPhotosTheme {
        LegacyAppShell(
            palette = p,
            selected = LegacyRootSection.FOLDERS,
            onHome = {}
        ) { compact ->
            HierarchyRootScreen(
                folders = previewTree,
                palette = p,
                compact = compact,
                rootViewMode = FolderViewMode.GALLERY,
                showOt = true,
                onRootViewMode = {}, onOpen = {}, onNewFolder = {}, onEditOt = {}, onRename = {},
                onDuplicate = {}, onCloneStructure = {}, onConfigure = {}, onToggleCollapsed = {}, onSetAllCollapsed = {},
                onMoveFolders = { _, _ -> }, onExport = {}, onDelete = {}
            )
        }
    }
}

@Preview(name = "V10 · Splash", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun SplashPreview() {
    InspeccionPhotosSplash(legacyPalette(true))
}
