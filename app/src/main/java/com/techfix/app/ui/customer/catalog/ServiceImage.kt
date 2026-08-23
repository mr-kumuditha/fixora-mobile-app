package com.techfix.app.ui.customer.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.techfix.app.R
import com.techfix.app.domain.catalog.RepairService

/** Shared Coil-backed image treatment for catalog and detail surfaces. */
@Composable
fun ServiceImage(
    service: RepairService,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(
        model = service.catalogImage(),
        contentDescription = service.catalogImageDescription(),
        contentScale = contentScale,
        placeholder = painterResource(R.drawable.img_repair_bench),
        error = painterResource(R.drawable.img_repair_bench),
        modifier = modifier,
    )
}
