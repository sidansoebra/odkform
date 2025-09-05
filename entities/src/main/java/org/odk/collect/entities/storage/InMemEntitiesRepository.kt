package org.odk.collect.entities.storage

import org.odk.collect.entities.javarosa.parse.EntitySchema
import org.odk.collect.shared.Query

class InMemEntitiesRepository : EntitiesRepository {

    private val lists = mutableSetOf<String>()
    private val listProperties = mutableMapOf<String, MutableSet<String>>()
    private val listVersions = mutableMapOf<String, String>()
    private val entities = mutableMapOf<String, MutableList<Entity.New>>()

    override fun getLists(): Set<String> {
        return lists
    }

    override fun getCount(list: String): Int {
        return query(list).count()
    }

    override fun addList(list: String) {
        lists.add(list)
    }

    override fun delete(list: String, id: String) {
        entities[list]?.removeIf { it.id == id }
    }

    override fun query(list: String, query: Query?): List<Entity.Saved> {
        val entities = (entities[list] ?: emptyList()).mapIndexed { index, entity ->
            Entity.Saved(
                entity.id,
                entity.label,
                entity.version,
                buildProperties(list, entity),
                entity.state,
                index,
                entity.trunkVersion,
                entity.branchId
            )
        }

        fun Entity.getFieldValue(column: String): String = when (column) {
            EntitySchema.ID -> id
            EntitySchema.LABEL -> label!!
            EntitySchema.VERSION -> version.toString()
            else -> properties.find { it.first == column }?.second
                ?: throw QueryException("No such column: $column")
        }

        return when (query) {
            is Query.StringEq -> entities.filter { it.getFieldValue(query.column) == query.value }
            is Query.StringNotEq -> entities.filter { it.getFieldValue(query.column) != query.value }
            is Query.NumericEq -> entities.filter { it.getFieldValue(query.column).toDoubleOrNull() == query.value }
            is Query.NumericNotEq -> entities.filter { it.getFieldValue(query.column).toDoubleOrNull() != query.value }
            is Query.And -> query(list, query.queryA).intersect(query(list, query.queryB)).toList()
            is Query.Or -> query(list, query.queryA).union(query(list, query.queryB)).toList()
            null -> entities
        }
    }

    override fun getByIndex(list: String, index: Int): Entity.Saved? {
        return query(list).firstOrNull { it.index == index }
    }

    override fun updateListHash(list: String, hash: String) {
        listVersions[list] = hash
    }

    override fun getListHash(list: String): String? {
        return listVersions[list]
    }

    override fun save(list: String, vararg entities: Entity) {
        val entityList = this.entities.getOrPut(list) { mutableListOf() }

        entities.forEach { entity ->
            updateLists(list, entity)
            val existing = entityList.find { it.id == entity.id }

            if (existing != null) {
                val state = when (existing.state) {
                    Entity.State.OFFLINE -> entity.state
                    Entity.State.ONLINE -> Entity.State.ONLINE
                }

                entityList.remove(existing)
                entityList.add(
                    Entity.New(
                        entity.id,
                        entity.label ?: existing.label,
                        version = entity.version,
                        properties = mergeProperties(existing, entity),
                        state = state,
                        trunkVersion = entity.trunkVersion,
                        branchId = entity.branchId
                    )
                )
            } else {
                entityList.add(
                    Entity.New(
                        entity.id,
                        entity.label,
                        entity.version,
                        entity.properties,
                        entity.state,
                        entity.trunkVersion,
                        entity.branchId
                    )
                )
            }
        }
    }

    private fun updateLists(list: String, entity: Entity) {
        lists.add(list)
        val properties = listProperties.getOrPut(list) {
            mutableSetOf()
        }
        properties.addAll(
            entity
                .properties
                .map { it.first }
                .distinctBy { it.lowercase() }
                .filterNot { properties.any { property -> property.equals(it, ignoreCase = true) } }
        )
    }

    private fun mergeProperties(
        existing: Entity,
        new: Entity
    ): List<Pair<String, String>> {
        val existingProperties = mutableMapOf(*existing.properties.toTypedArray())
        new.properties.forEach {
            existingProperties[it.first] = it.second
        }

        return existingProperties.map { Pair(it.key, it.value) }
    }

    private fun buildProperties(list: String, entity: Entity.New): List<Pair<String, String>> {
        return listProperties[list]?.map { property ->
            Pair(
                property,
                entity.properties.find { it.first == property }?.second ?: ""
            )
        } ?: emptyList()
    }
}
