package staff.android.adaptive

import android.os.Bundle
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class OwnerAndStoreTest {
    private val owners=ViewModelStore()
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private class Repo : CartRepository {
        override val lines=MutableStateFlow(mapOf("camera" to CartLine(),"tea" to CartLine()))
        var gate: CompletableDeferred<Unit>?=null
        var calls=0
        var resetFails=false
        override suspend fun confirm(id:String,baseRevision:Long,quantity:Int) {
            calls++; gate?.await()
            val old=lines.value.getValue(id)
            if(old.revision!=baseRevision) throw RevisionConflict()
            lines.value=lines.value+(id to CartLine(quantity,old.revision+1))
        }
        override suspend fun reset() { if(resetFails) throw IOException("fixture"); lines.value=mapOf("camera" to CartLine(),"tea" to CartLine()) }
    }
    private fun owner(repo:CartRepository,saved:SavedStateHandle=SavedStateHandle()):CartViewModel {
        lateinit var result:CartViewModel
        main { result=CartViewModel(saved,repo); owners.put("owner-${owners.keys().size}",result) }
        runBlocking { withTimeout(5000) {result.state.first {it.lines.isNotEmpty()}} }
        return result
    }
    private fun await(owner:CartViewModel,condition:(CartState)->Boolean):CartState = runBlocking {withTimeout(5000) {owner.state.first(condition)}}
    @After fun clear() {main {owners.clear()}}
    @Test fun selectingAnotherItemNeverSubmitsAndRetainsPerIdDrafts() {
        val repo=Repo(); val vm=owner(repo)
        main {vm.select("camera");vm.edit("camera");vm.change("camera","3");vm.select("tea");vm.edit("tea");vm.change("tea","");vm.select("camera")}
        val s=await(vm) {it.selectedId=="camera" && it.drafts["tea"]?.raw==""}
        assertEquals("3",s.drafts["camera"]!!.raw);assertEquals(0,repo.calls)
    }
    @Test fun reconstructionRestoresSelectionAndEmptyDraftAgainstCurrentData() {
        val repo=Repo();val saved=SavedStateHandle();val vm=owner(repo,saved)
        main {vm.select("camera");vm.edit("camera");vm.change("camera","")}
        await(vm) {it.drafts["camera"]?.raw==""}
        lateinit var restored:SavedStateHandle
        main {restored=SavedStateHandle(mapOf("selection" to saved.get<String>("selection"),"drafts" to Bundle(saved.get<Bundle>("drafts")!!)))}
        repo.lines.value=repo.lines.value+("camera" to CartLine(4,1))
        val next=owner(repo,restored);val s=await(next) {it.drafts["camera"]!=null}
        assertEquals("",s.drafts["camera"]!!.raw);assertTrue(s.isStale("camera"));assertFalse(s.canConfirm("camera"));assertEquals(0,repo.calls)
    }
    @Test fun pendingWriteFailureRetainsItsOriginAfterSelectionChanges() {
        val repo=Repo();repo.gate=CompletableDeferred();val vm=owner(repo)
        main {vm.select("camera");vm.edit("camera");vm.change("camera","3");vm.confirm("camera");vm.confirm("camera");vm.select("tea")}
        await(vm) {it.busy && it.selectedId=="tea"};assertEquals(1,repo.calls)
        repo.gate!!.completeExceptionally(IOException("controlled write failure"))
        val s=await(vm) {!it.busy && it.issue==CartIssue.Save}
        assertEquals("camera",s.issueItemId);assertEquals("3",s.drafts["camera"]!!.raw)
        main {vm.edit("tea");vm.change("tea","4")}
        assertEquals("camera",await(vm) {it.drafts["tea"]?.raw=="4"}.issueItemId)
    }
    @Test fun confirmUsesTheLatestSynchronousRawAndRejectsStaleRevision() {
        val repo=Repo();val vm=owner(repo)
        main {vm.edit("camera");vm.change("camera","3");vm.confirm("camera")}
        assertEquals(3,await(vm) {it.lines["camera"]?.quantity==3}.lines["camera"]!!.quantity)
        await(vm) {!it.busy};main {vm.edit("camera");vm.change("camera","4")}
        repo.lines.value=repo.lines.value+("camera" to CartLine(1,5))
        await(vm) {it.isStale("camera")};main {vm.confirm("camera")};assertEquals(1,repo.calls)
    }
    @Test fun resetFailureHasItsOwnContract() {
        val repo=Repo().apply {resetFails=true};val vm=owner(repo)
        main {vm.reset()}
        val s=await(vm) {it.issue==CartIssue.Reset};assertNull(s.issueItemId);assertTrue(s.drafts.isEmpty())
    }
    @Test fun dataStoreWritesArePerIdAndCheckRevisionInsideTransaction() = runBlocking<Unit> {
        val file=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"c8-${System.nanoTime()}.preferences_pb")
        var scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        try {
            val repo=DataStoreCartRepository(PreferenceDataStoreFactory.create(scope=scope,produceFile={file}))
            repo.confirm("camera",0,3)
            try {repo.confirm("camera",0,4);fail("stale revision accepted")} catch(expected:RevisionConflict) {}
            assertEquals(2,repo.lines.first().getValue("tea").quantity)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
            val reopened=DataStoreCartRepository(PreferenceDataStoreFactory.create(scope=scope,produceFile={file}))
            assertEquals(CartLine(3,1),reopened.lines.first().getValue("camera"))
        } finally {scope.coroutineContext[Job]!!.cancelAndJoin();file.delete()}
    }
}
