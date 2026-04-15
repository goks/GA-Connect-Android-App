package com.example.pricelist.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File


class BrochureRepository {

    private val fs      = Firebase.firestore
    private val storage = Firebase.storage.reference.child("brochures")

    /** pull once from Firestore */
    suspend fun fetchBrochures(): List<Brochure> =
        fs.collection("brochures")
            .orderBy("name")
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(Brochure::class.java)?.copy(id = doc.id)
            }

    /**
     *  ▶ download (if needed) and return the local Uri.
     *  @param onProgress (0f–1f) – use to show progress bar
     */
    suspend fun getLocalFile(
        ctx: Context,
        brochure: Brochure,
        onProgress: (Float) -> Unit = {}
    ): Uri = withContext(Dispatchers.IO) {

        val dir  = File(ctx.filesDir, "brochures").apply { mkdirs() }
        val file = File(dir, brochure.file)

        if (!file.exists()) {
            val gsRef = storage.child(brochure.file)
            val task  = gsRef.getFile(file)

            task.addOnProgressListener {
                val pct = it.bytesTransferred.toFloat() / it.totalByteCount.toFloat()
                onProgress(pct)
            }.await()   // suspend until done
        }

        return@withContext FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            file
        )
    }
}
