package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 获取所有联系人处理器
 */
class GetAllContactsHandler : ActionHandler {
    override val actionName = "get_all_contacts"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val limit = context.params?.limit ?: 0
        val offset = context.params?.offset ?: 0
        val contacts = context.contactsService.getAllContacts(limit, offset)
        val contactsList = contacts.map { contact ->
            mapOf(
                "id" to contact.id,
                "displayName" to contact.displayName,
                "phoneNumber" to contact.phoneNumber,
                "photoUri" to contact.photoUri,
                "starred" to contact.starred
            )
        }
        context.wrappedCallback(CommandResult(true, "获取联系人成功", mapOf(
            "contacts" to contactsList,
            "count" to contacts.size
        )))
    }
}
