package com.dmide.currencies.ui

import android.util.Log
import android.view.View
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.dmide.currencies.R
import com.dmide.currencies.model.BASE_CURRENCY
import com.dmide.currencies.model.Currency
import com.dmide.currencies.model.CurrencyRepository
import io.reactivex.disposables.CompositeDisposable

class CurrenciesViewModel(private val repository: CurrencyRepository) : ViewModel() {
    val loadingVisibilityLiveData: LiveData<Int> = MutableLiveData()
    val errorMessageLiveData: LiveData<ErrorMessage> = MutableLiveData()
    val currenciesLiveData: CurrenciesLiveData = CurrenciesLiveData()
    val scrollStateLiveData: LiveData<Int> = MutableLiveData()

    var selectedCurrencyName: String = BASE_CURRENCY
        private set

    // for correct exchange result when changing the base currency
    private val baseCurrencyRateSnapshots: MutableMap<String,Float> = mutableMapOf()
    private var portfolio: Portfolio = Portfolio(BASE_CURRENCY, 100f)
    private val compositeDisposable: CompositeDisposable = CompositeDisposable()

    init {
        (loadingVisibilityLiveData as MutableLiveData).value = View.VISIBLE

        val currenciesDisposable = repository.currency
            .subscribe { pair ->
                val newBaseCurrencyName = pair.first
                val currencies = pair.second

                if (newBaseCurrencyName != portfolio.currencyName) {
                    val rateSnapshot = baseCurrencyRateSnapshots[newBaseCurrencyName]!! // should be present
                    portfolio = Portfolio(newBaseCurrencyName, portfolio.amount * rateSnapshot)
                }

                currenciesLiveData.update(currencies)
            }

        val statusDisposable = repository.status
            .subscribe { status ->
                when (status) {
                    is CurrencyRepository.Status.LoadingFinished -> {
                        (errorMessageLiveData as MutableLiveData).value = null
                        loadingVisibilityLiveData.value = View.GONE
                    }
                    is CurrencyRepository.Status.LoadingFailed -> {
                        Log.e(javaClass.name, "Error retrieving currencies", status.t)
                        (errorMessageLiveData as MutableLiveData).value = ErrorMessage(R.string.error_loading)
                    }
                }
            }

        compositeDisposable.addAll(statusDisposable, currenciesDisposable)
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.dispose()
    }

    fun onNewScrollState(scrollState: Int) {
        (scrollStateLiveData as MutableLiveData).value = scrollState
    }

    fun onCurrencySelected(currency: Currency) {
        selectedCurrencyName = currency.name
        baseCurrencyRateSnapshots[currency.name] = currency.rate
        currenciesLiveData.update()
        repository.changeBaseCurrency(currency.name)
    }

    fun onAmountUpdated(amount: Float) {
        portfolio = Portfolio(portfolio.currencyName, amount)
        val copy = currenciesLiveData.value?.map { it.copy() }
        currenciesLiveData.update(copy)
    }

    data class ErrorMessage(@StringRes val text: Int)

    data class Portfolio(val currencyName: String, val amount: Float)

    inner class CurrenciesLiveData : MutableLiveData<List<Currency>>() {

        fun update(currencies: List<Currency>? = value) {
            // sort here is a bit hacky but simple: we take empty string as the lowest possible value
            // when sorting by name to promote the selected currency to the top
            value = currencies
                ?.sortedBy { if (it.name == selectedCurrencyName) "" else it.name }
                ?.apply { forEach { it.value = it.rate * portfolio.amount } } // update values
        }
    }

}