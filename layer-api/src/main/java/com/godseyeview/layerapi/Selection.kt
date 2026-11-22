package com.godseyeview.layerapi

/** Plan Q28: generic selection and follow/track contracts.
 *  The core renders the detail card from [Entity.cardFields]; a layer never
 *  draws its own card. Follow mode hands the core a moving target id and the
 *  core camera keeps it centred until the user takes the camera back. */
data class FollowTarget(val layerId: String, val entityId: String)

interface SelectionListener {
    fun onSelected(entity: Entity?)
    fun onFollowChanged(target: FollowTarget?)
}
