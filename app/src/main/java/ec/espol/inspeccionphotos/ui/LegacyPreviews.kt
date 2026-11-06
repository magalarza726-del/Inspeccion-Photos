package ec.espol.inspeccionphotos.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ec.espol.inspeccionphotos.CodeItem
import ec.espol.inspeccionphotos.Inspection
import ec.espol.inspeccionphotos.PhotoItem

private val legacyPreviewInspections = listOf(
    Inspection("a", "2026-05-22_OT-000", "2026-05-22", "000", listOf(CodeItem("001"), CodeItem("002"))),
    Inspection("b", "2026-05-26_Mabe", "2026-05-26", "", listOf(CodeItem("001"), CodeItem("002"))),
    Inspection("c", "2026-06-05_Mabe", "2026-06-05", "", listOf(CodeItem("001")))
)

private val legacyPreviewDetail = Inspection(
    id = "preview-detail",
    name = "2026-07-09_PRUEBA",
    created = "2026-07-09",
    ot = "7899",
    codes = listOf(
        CodeItem(
            "001",
            "Sin observación general",
            listOf(
                PhotoItem("001_02.jpg", "/preview/001_02.jpg"),
                PhotoItem("001_01.jpg", "/preview/001_01.jpg")
            )
        ),
        CodeItem("002", "Sin observación general")
    )
)

@Preview(name = "ORIGINAL · Carpetas horizontal", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun LegacyFoldersLandscapePreview() {
    val palette = legacyPalette(true)
    InspeccionPhotosTheme {
        Surface {
            LegacyAppShell(
                palette = palette,
                selected = LegacyRootSection.FOLDERS,
                onHome = {}
            ) { compact ->
                LegacyFoldersScreen(
                    inspections = legacyPreviewInspections,
                    palette = palette,
                    compact = compact,
                    onOpen = {}, onNewFolder = {}, onEditOt = {}, onRename = {}, onClone = {}, onExport = {}, onDelete = {}
                )
            }
        }
    }
}

@Preview(name = "ORIGINAL · Inspección horizontal", widthDp = 1280, heightDp = 720, showBackground = true)
@Composable
private fun LegacyInspectionLandscapePreview() {
    val palette = legacyPalette(true)
    InspeccionPhotosTheme {
        Surface {
            LegacyAppShell(
                palette = palette,
                selected = LegacyRootSection.FOLDERS,
                onHome = {}
            ) { compact ->
                LegacyInspectionDetailScreen(
                    inspection = legacyPreviewDetail,
                    palette = palette,
                    compact = compact,
                    onBack = {}, onSaveOt = {}, onAddCode = {}, onEditCodeObservation = {}, onRenameCode = {}, onDeleteCode = {},
                    onTakePhoto = {}, onPickPhoto = {}, onRenamePhoto = { _, _ -> }, onEditPhotoObservation = { _, _ -> },
                    onDownloadPhoto = { _, _ -> }, onDeletePhoto = { _, _ -> }, onExport = {}
                )
            }
        }
    }
}

@Preview(name = "ORIGINAL · Carpetas vertical", widthDp = 412, heightDp = 860, showBackground = true)
@Composable
private fun LegacyFoldersPortraitPreview() {
    val palette = legacyPalette(true)
    InspeccionPhotosTheme {
        Surface {
            LegacyAppShell(
                palette = palette,
                selected = LegacyRootSection.FOLDERS,
                onHome = {}
            ) { compact ->
                LegacyFoldersScreen(
                    inspections = legacyPreviewInspections,
                    palette = palette,
                    compact = compact,
                    onOpen = {}, onNewFolder = {}, onEditOt = {}, onRename = {}, onClone = {}, onExport = {}, onDelete = {}
                )
            }
        }
    }
}
