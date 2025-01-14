package com.tonapps.tonkeeper.ui.screen.staking.stake.details

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.flexbox.FlexboxLayout
import com.tonapps.extensions.getParcelableCompat
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.staking.entities.PoolEntity
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity
import uikit.extensions.dp
import uikit.extensions.drawable
import uikit.extensions.inflate
import uikit.extensions.setLeftDrawable
import uikit.navigation.Navigation.Companion.navigation

class StakeDetailsFragment: StakingScreen.ChildFragment(R.layout.fragment_stake_details) {

    private val poolInfo: PoolInfoEntity by lazy { requireArguments().getParcelableCompat(POOL_KEY)!! }
    private val pool: PoolEntity by lazy { poolInfo.pools.first() }

    private lateinit var linkDrawable: Drawable
    private lateinit var linksView: FlexboxLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        linkDrawable = requireContext().drawable(UIKitIcon.ic_globe_16)
        val apyView = view.findViewById<AppCompatTextView>(R.id.pool_apy)
        apyView.text = "≈ ${pool.apy}%"

        val minDepositView = view.findViewById<AppCompatTextView>(R.id.pool_min_deposit)
        minDepositView.text = CurrencyFormatter.format(TokenEntity.TON.symbol, pool.minStake)

        linksView = view.findViewById(R.id.links)
        applyLinks(poolInfo.details.getLinks(pool.address))

        val chooseButton = view.findViewById<Button>(R.id.choose_button)
        chooseButton.setOnClickListener {
            stakeViewModel.selectPool(pool)
            finish()
        }
    }

    private fun applyLinks(links: List<String>) {
        linksView.removeAllViews()
        for (link in links) {
            val host = Uri.parse(link).host!!
            val linkView = requireContext().inflate(R.layout.view_link, linksView) as AppCompatTextView
            linkView.text = host
            linkView.setLeftDrawable(linkDrawable)
            linkView.setOnClickListener { navigation?.openURL(link, true) }
            linksView.addView(linkView)
            linksView.addView(View(context), ViewGroup.LayoutParams(8.dp, 0))
        }
    }

    override fun getTitle() = pool.name

    companion object {
        private const val POOL_KEY = "pool"

        fun newInstance(pool: PoolInfoEntity): StakeDetailsFragment {
            val fragment = StakeDetailsFragment()
            fragment.arguments = Bundle().apply {
                putParcelable(POOL_KEY, pool)
            }
            return fragment
        }
    }
}