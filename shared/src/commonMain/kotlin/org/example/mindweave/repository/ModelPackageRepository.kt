package org.example.mindweave.repository

import org.example.mindweave.ai.ModelPackage

interface ModelPackageRepository {
    suspend fun listModelPackages(): List<ModelPackage>

    suspend fun getModelPackage(packageId: String): ModelPackage?

    suspend fun upsertModelPackage(modelPackage: ModelPackage)
}
