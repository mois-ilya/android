package com.tonapps.tonkeeper.ui.screen.init

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.Coin
import com.tonapps.blockchain.ton.contract.WalletV4R2Contract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.blockchain.ton.extensions.toWalletAddress
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.password.PasscodeRepository
import com.tonapps.tonkeeper.ui.screen.init.list.AccountItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.AccountDetailsEntity
import com.tonapps.wallet.api.entity.AccountEntity
import com.tonapps.wallet.data.account.WalletColor
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.WalletSource
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.entities.WalletLabel
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.api.pub.PublicKeyEd25519
import org.ton.block.AddrStd
import org.ton.crypto.hex
import org.ton.mnemonic.Mnemonic
import uikit.extensions.context

@OptIn(FlowPreview::class)
class InitViewModel(
    private val type: InitArgs.Type,
    application: Application,
    private val passcodeRepository: PasscodeRepository,
    private val walletRepository: WalletRepository,
    private val tokenRepository: TokenRepository,
    private val collectiblesRepository: CollectiblesRepository,
    private val settingsRepository: SettingsRepository,
    private val api: API,
    private val backupRepository: BackupRepository,
    savedStateHandle: SavedStateHandle
): AndroidViewModel(application) {

    private val passcodeAfterSeed = false // type == InitArgs.Type.Import || type == InitArgs.Type.Testnet
    private val savedState = InitModelState(savedStateHandle)
    private val testnet: Boolean = type == InitArgs.Type.Testnet

    private val _uiTopOffset = MutableStateFlow(0)
    val uiTopOffset = _uiTopOffset.asStateFlow()

    private val _eventFlow = MutableSharedFlow<InitEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val eventFlow = _eventFlow.asSharedFlow().filterNotNull()

    private val _watchAccountResolveFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val watchAccountFlow = _watchAccountResolveFlow.asSharedFlow()
        .debounce(1000)
        .filter { it.isNotBlank() }
        .map {
            val account = api.resolveAddressOrName(it, testnet)
            if (account == null || !account.active) {
                setWatchAccount(null)
                return@map null
            }
            setWatchAccount(account)
            account
        }.flowOn(Dispatchers.IO)

    private val _accountsFlow = MutableEffectFlow<List<AccountItem>?>()
    val accountsFlow = _accountsFlow.asSharedFlow().filterNotNull()

    private val hasPushPermission: Boolean
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }
            return true
        }

    init {
        if (passcodeAfterSeed) {
            _eventFlow.tryEmit(InitEvent.Step.ImportWords)
        } else  if (!passcodeRepository.hasPinCode) {
            _eventFlow.tryEmit(InitEvent.Step.CreatePasscode)
        } else {
            startWalletFlow()
        }
    }

    fun toggleAccountSelection(address: String) {
        val items = getAccounts().toMutableList()
        val oldItem = items.find { it.address.toRawAddress() == address } ?: return
        val newItem = oldItem.copy(selected = !oldItem.selected)
        items[items.indexOf(oldItem)] = newItem
        setAccounts(items)
    }

    private fun startWalletFlow() {
        if (type == InitArgs.Type.Watch) {
            _eventFlow.tryEmit(InitEvent.Step.WatchAccount)
        } else if (type == InitArgs.Type.New) {
            _eventFlow.tryEmit(InitEvent.Step.LabelAccount)
        } else if (type == InitArgs.Type.Import || type == InitArgs.Type.Testnet) {
            _eventFlow.tryEmit(InitEvent.Step.ImportWords)
        } else if (type == InitArgs.Type.Signer) {
            _eventFlow.tryEmit(InitEvent.Step.LabelAccount)
        }
    }

    fun setUiTopOffset(offset: Int) {
        _uiTopOffset.value = offset
    }

    fun routePopBackStack() {
        _eventFlow.tryEmit(InitEvent.Back)
    }

    fun resolveWatchAccount(value: String) {
        _watchAccountResolveFlow.tryEmit(value)
    }

    fun setPasscode(passcode: String) {
        savedState.passcode = passcode

        _eventFlow.tryEmit(InitEvent.Step.ReEnterPasscode)
    }

    fun reEnterPasscode(passcode: String) {
        val valid = savedState.passcode == passcode
        if (!valid) {
            routePopBackStack()
            return
        }
        startWalletFlow()
    }

    suspend fun setMnemonic(words: List<String>) {
        resolveWallets(words)
        savedState.mnemonic = words
    }

    fun setPublicKey(publicKey: PublicKeyEd25519?) {
        savedState.publicKey = publicKey
    }

    private suspend fun resolveWallets(mnemonic: List<String>) = withContext(Dispatchers.IO) {
        val seed = Mnemonic.toSeed(mnemonic)
        savedState.seed = seed
        val privateKey = PrivateKeyEd25519(seed)
        val publicKey = privateKey.publicKey()
        val accounts = api.resolvePublicKey(publicKey, testnet).filter {
            it.isWallet && it.walletVersion != WalletVersion.UNKNOWN && it.active
        }.sortedByDescending { it.walletVersion.index }.toMutableList()

        /*if (accounts.size == 0 && type == InitArgs.Type.Import) {
            throw IllegalStateException("No valid accounts found")
        }*/

        if (accounts.count { it.walletVersion == WalletVersion.V4R2 } == 0) {
            val contract = WalletV4R2Contract(publicKey = publicKey)
            accounts.add(0, AccountDetailsEntity(
                query = "",
                preview = AccountEntity(
                    address = contract.address.toWalletAddress(testnet),
                    accountId = contract.address.toAccountId(),
                    name = null,
                    iconUri = null,
                    isWallet = true,
                    isScam = false,
                ),
                active = true,
                walletVersion = WalletVersion.V4R2,
                balance = 0
            ))
        }

        val deferredTokens = mutableListOf<Deferred<List<AccountTokenEntity>>>()
        for (account in accounts) {
            deferredTokens.add(async { getTokens(account.address) })
        }

        val deferredCollectibles = mutableListOf<Deferred<List<NftEntity>>>()
        for (account in accounts) {
            deferredCollectibles.add(async { collectiblesRepository.getRemoteNftItems(account.address, testnet) })
        }

        val items = mutableListOf<AccountItem>()
        for ((index, account) in accounts.withIndex()) {
            val balance = Coin.toCoins(account.balance)
            val hasTokens = deferredTokens[index].await().size > 1
            val hasCollectibles = deferredCollectibles[index].await().isNotEmpty()
            val item = AccountItem(
                address = AddrStd(account.address).toWalletAddress(testnet),
                name = account.name,
                walletVersion = account.walletVersion,
                balanceFormat = CurrencyFormatter.format("TON", balance),
                tokens = hasTokens,
                collectibles = hasCollectibles,
                selected = account.walletVersion == WalletVersion.V4R2 || (account.balance > 0 || hasTokens || hasCollectibles),
                position = ListCell.getPosition(accounts.size, index)
            )
            items.add(item)
        }
        setAccounts(items)

        if (items.size > 1) {
            _eventFlow.tryEmit(InitEvent.Step.SelectAccount)
        } else {
            _eventFlow.tryEmit(InitEvent.Step.LabelAccount)
        }
    }

    private suspend fun getTokens(accountId: String): List<AccountTokenEntity> {
        return tokenRepository.getRemote(settingsRepository.currency, accountId, testnet)
    }

    private fun setWatchAccount(account: AccountDetailsEntity?) {
        val oldAccount = getWatchAccount()
        if (oldAccount?.equals(account) == true) {
            return
        }

        savedState.watchAccount = account
        setLabelName(account?.name ?: "Wallet")
    }

    fun getWatchAccount(): AccountDetailsEntity? {
        return savedState.watchAccount
    }

    private fun getAccounts(): List<AccountItem> {
        return savedState.accounts ?: emptyList()
    }

    private fun setAccounts(accounts: List<AccountItem>) {
        savedState.accounts = accounts
        _accountsFlow.tryEmit(accounts)
    }

    private fun getSelectedAccounts(): List<AccountItem> {
        return getAccounts().filter { it.selected }
    }

    fun setLabel(name: String, emoji: String, color: Int) {
        setLabel(WalletLabel(name, emoji, color))
    }

    fun setLabel(label: WalletLabel) {
        savedState.label = label
    }

    fun getLabel(): WalletLabel {
        return savedState.label ?: WalletLabel("","\uD83D\uDE00", WalletColor.all.first())
    }

    fun setLabelName(name: String) {
        val oldLabel = getLabel()
        setLabel(oldLabel.copy(accountName = name))
    }

    fun setPush() {
        nextStep(InitEvent.Step.Push)
    }

    fun nextStep(from: InitEvent.Step) {
        if (from == InitEvent.Step.WatchAccount) {
            _eventFlow.tryEmit(InitEvent.Step.LabelAccount)
        } else if (from == InitEvent.Step.SelectAccount) {
            applyNameFromSelectedAccounts()
            _eventFlow.tryEmit(InitEvent.Step.LabelAccount)
        } else if (from == InitEvent.Step.Push) {
            execute()
        } else if (!hasPushPermission) {
            _eventFlow.tryEmit(InitEvent.Step.Push)
        } else if (passcodeAfterSeed && from == InitEvent.Step.ImportWords) {
            _eventFlow.tryEmit(InitEvent.Step.CreatePasscode)
        } else {
            execute()
        }
    }

    private fun applyNameFromSelectedAccounts() {
        val selected = getSelectedAccounts().filter { !it.name.isNullOrBlank() }
        if (selected.isEmpty()) {
            return
        }
        val name = selected.first().name ?: return
        setLabelName(name)
    }

    private fun execute() {
        _eventFlow.tryEmit(InitEvent.Loading(true))

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!passcodeRepository.hasPinCode) {
                    passcodeRepository.set(savedState.passcode!!)
                }

                val wallets = mutableListOf<WalletEntity>()
                when (type) {
                    InitArgs.Type.Watch -> wallets.add(saveWatchWallet())
                    InitArgs.Type.New -> wallets.add(createNewWallet())
                    InitArgs.Type.Import, InitArgs.Type.Testnet -> wallets.addAll(importWallet())
                    InitArgs.Type.Signer -> wallets.add(signerWallet())
                }

                if (type != InitArgs.Type.New) {
                    for (wallet in wallets) {
                        backupRepository.addBackup(wallet.id, BackupEntity.Source.LOCAL)
                    }
                }

                for (wallet in wallets) {
                    settingsRepository.setPushWallet(wallet.id, true)
                }
                _eventFlow.tryEmit(InitEvent.Finish)
            } catch (e: Throwable) {
                _eventFlow.tryEmit(InitEvent.Loading(false))
            }
        }
    }

    private suspend fun createNewWallet(): WalletEntity {
        return walletRepository.createNewWallet(getLabel())
    }

    private suspend fun saveWatchWallet(): WalletEntity {
        val account = getWatchAccount() ?: throw IllegalStateException("Account is not set")
        val label = getLabel()
        val publicKey = getPublicKey(account.address)

        return walletRepository.addWatchWallet(publicKey, label, account.walletVersion, WalletSource.Default)
    }

    private suspend fun importWallet(): List<WalletEntity> {
        val versions = getSelectedAccounts().map { it.walletVersion }
        val mnemonic = savedState.mnemonic ?: throw IllegalStateException("Mnemonic is not set")
        val seed = savedState.seed ?: throw IllegalStateException("Seed is not set")
        val label = getLabel()
        val privateKey = PrivateKeyEd25519(seed)
        val publicKey = privateKey.publicKey()

        return walletRepository.addWallets(mnemonic, publicKey, versions, label.name, label.emoji, label.color, testnet)
    }

    private suspend fun signerWallet(): WalletEntity {
        val label = getLabel()
        val publicKey = savedState.publicKey ?: throw IllegalStateException("Public key is not set")
        val walletSource = savedState.walletSource ?: throw IllegalStateException("Wallet source is not set")

        return walletRepository.addSignerWallet(publicKey, label.name, label.emoji, label.color, walletSource)
    }

    private fun getPublicKey(
        accountId: String,
    ): PublicKeyEd25519 {
        api.getPublicKey(accountId, testnet).let { hex ->
            return PublicKeyEd25519(hex(hex))
        }
    }

}