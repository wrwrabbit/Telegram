package org.telegram.messenger.fakepasscode

import android.util.Base64
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

class AccountActions : Action {
    @FakePasscodeSerializer.Ignore
    var accountNum: Int? = null
    var removeChatsAction = RemoveChatsAction()
        private set(value) { field = value; SharedConfig.saveConfig() }
    var telegramMessageAction = TelegramMessageAction()
        private set(value) { field = value; SharedConfig.saveConfig() }
    private var deleteContactsAction: DeleteContactsAction? = null
        private set(value) { field = value; SharedConfig.saveConfig() }
    private var deleteStickersAction: DeleteStickersAction? = null
        private set(value) { field = value; SharedConfig.saveConfig() }
    private var clearSearchHistoryAction: ClearSearchHistoryAction? = null
        private set(value) { field = value; SharedConfig.saveConfig() }
    private var clearBlackListAction: ClearBlackListAction? = null
        private set(value) { field = value; SharedConfig.saveConfig() }
    private var clearSavedChannelsAction: ClearSavedChannelsAction? = null
        private set(value) { field = value; SharedConfig.saveConfig() }
    private var clearDraftsAction: ClearDraftsAction? = null
        private set(value) { field = value; SharedConfig.saveConfig() }
    var terminateOtherSessionsAction = TerminateOtherSessionsAction()
        private set(value) { field = value; SharedConfig.saveConfig() }
    private var logOutAction: LogOutAction? = null
        private set(value) { field = value; SharedConfig.saveConfig() }
    private var hideAccountAction: HideAccountAction? = null
        private set(value) { field = value; SharedConfig.saveConfig() }
    var fakePhone = ""
        set(value) { field = value; SharedConfig.saveConfig() }
    var sessionsToHide = CheckedSessions()
        private set(value) { field = value; SharedConfig.saveConfig() }
    private var salt: String? = null
        private set(value) { field = value; SharedConfig.saveConfig() }
    var idHash: String? = null
        private set(value) { field = value; SharedConfig.saveConfig() }

    companion object {
        var updateIdHashEnabled = true
    }

    init {
        if (updateIdHashEnabled) {
            Utilities.globalQueue.postRunnable(UpdateIdHashRunnable(this), 1000)
        }
    }

    override fun execute(fakePasscode: FakePasscode) {
        accountNum?.let { acc ->
            fakePasscode.actionsResult.putFakePhoneNumber(acc, fakePhone)
            listOfNotNull(
                removeChatsAction, telegramMessageAction, deleteContactsAction,
                deleteStickersAction, clearSearchHistoryAction, clearBlackListAction,
                clearSavedChannelsAction, clearDraftsAction, terminateOtherSessionsAction,
                hideAccountAction
            ).forEach { action ->
                    action.setAccountNum(acc)
                    action.execute(fakePasscode)
            }

            logOutAction?.let {
                it.setAccountNum(acc)
                CheckLogOutActionRunnable(it, fakePasscode).run()
            }
        }
    }

    private fun getSalt(): ByteArray {
        if (salt == null) {
            val saltBytes = ByteArray(16)
            Utilities.random.nextBytes(saltBytes)
            salt = Base64.encodeToString(saltBytes, Base64.DEFAULT)
        }
        return Base64.decode(salt, Base64.DEFAULT)
    }

    private fun calculateIdHash(user: TLRPC.User): String {
        val phoneDigits = user.phone.filter { it.isDigit() }
        if (phoneDigits.length < 4) {
            throw Exception("Can't calculate id hash: invalid phone")
        }
        val phoneId = phoneDigits.slice(phoneDigits.length - 4 until phoneDigits.length).toInt()
        val sum = (user.id % 10_000 + phoneId) % 10_000
        val sumBytes = sum.toString().encodeToByteArray()
        val bytes = ByteArray(32 + sumBytes.size)
        val salt = getSalt()
        System.arraycopy(salt, 0, bytes, 0, 16)
        System.arraycopy(sumBytes, 0, bytes, 16, sumBytes.size)
        System.arraycopy(salt, 0, bytes, sumBytes.size + 16, 16)
        return Utilities.bytesToHex(Utilities.computeSHA256(bytes, 0, bytes.size.toLong()))
    }

    fun checkIdHash() {
        if (accountNum != null) {
            accountNum?.let { account ->
                val userConfig = UserConfig.getInstance(account)
                if (userConfig.isConfigLoaded) {
                    if (userConfig.isClientActivated) {
                        idHash = calculateIdHash(userConfig.currentUser)
                    } else {
                        accountNum = null
                    }
                }
            }
        } else {
            checkAccountNum()
        }
    }

    fun checkAccountNum() {
        if (idHash != null) {
            for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
                val userConfig = UserConfig.getInstance(a)
                if (userConfig.isClientActivated) {
                    if (idHash == calculateIdHash(userConfig.currentUser)) {
                        for (fakePasscode in SharedConfig.fakePasscodes) {
                            val actions = fakePasscode.accountActions
                            if (actions.contains(this) && actions.stream().noneMatch { it.accountNum == a }) {
                                accountNum = a
                                break
                            }
                        }
                        break
                    }
                }
            }
        }
    }

    inline fun <reified T> reverse(action: T?): T? {
        if (action != null) {
            return null
        }
        return T::class.constructors.find { it.parameters.isEmpty() }?.call()
    }

    fun toggleDeleteContactsAction() { deleteContactsAction = reverse(deleteContactsAction) }
    fun toggleDeleteStickersAction() { deleteStickersAction = reverse(deleteStickersAction) }
    fun toggleClearSearchHistoryAction() { clearSearchHistoryAction = reverse(clearSearchHistoryAction) }
    fun toggleClearBlackListAction() { clearBlackListAction = reverse(clearBlackListAction) }
    fun toggleClearSavedChannelsAction() { clearSavedChannelsAction = reverse(clearSavedChannelsAction) }
    fun toggleClearDraftsAction() { clearDraftsAction = reverse(clearDraftsAction) }
    fun toggleLogOutAction() { logOutAction = reverse(logOutAction) }
    fun toggleHideAccountAction() { hideAccountAction = reverse(hideAccountAction) }

    fun setSessionsToHide(sessions: List<Long?>?) {
        sessionsToHide.sessions = sessions
        SharedConfig.saveConfig()
    }

    fun isDeleteContacts() = deleteContactsAction != null
    fun isDeleteStickers() = deleteStickersAction != null
    fun isClearSearchHistory() = clearSearchHistoryAction != null
    fun isClearBlackList() = clearBlackListAction != null
    fun isClearSavedChannels() = clearSavedChannelsAction != null
    fun isClearDraftsAction() = clearDraftsAction != null
    fun isLogOut() = logOutAction != null
    fun isHideAccount() = hideAccountAction != null
    fun isLogOutOrHideAccount() = logOutAction != null || hideAccountAction != null
    fun isPreventStickersBulletin() = deleteStickersAction?.isPreventBulletin ?: false

    fun getChatsToRemoveCount() = removeChatsAction.chatEntriesToRemove.size

    fun removeFakePhone() {
        fakePhone = ""
    }

    fun <T: AccountAction> setAction(action: T) {
        for (property in AccountActions::class.memberProperties) {
            if (property.returnType.javaType == action.javaClass && property is KMutableProperty<*>) {
                property.isAccessible = true
                property.setter.call(this, action)
            }
        }
    }
}