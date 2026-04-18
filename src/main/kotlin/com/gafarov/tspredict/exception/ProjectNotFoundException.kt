package com.gafarov.tspredict.exception

import java.util.UUID

class ProjectNotFoundException(id: UUID) : RuntimeException("Project with id=$id not found")