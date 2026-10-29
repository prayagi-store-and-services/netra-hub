package com.example
import com.example.data.engine.*
import org.junit.Assert.*
import org.junit.Test
class OfficialWeatherStateTest {
 @Test fun separateOutputStateDoesNotConsumeOtherChannel() {
  assertEquals(OutputDue(false,true),officialOutputsDue("Yellow","Yellow",null,true))
  assertEquals(OutputDue(true,false),officialOutputsDue("Yellow",null,"Yellow",true))
  assertEquals(OutputDue(false,false),officialOutputsDue("Green",null,null,false))
 }
 @Test fun reportsExpireAndAreaChangeInvalidates() {
  val n=1793241000000L;val r=OfficialReportSnapshot(3,n,n+1000,"Area","Report")
  assertTrue(reportVisible(r,3,n));assertFalse(reportVisible(r,4,n));assertFalse(reportVisible(r,3,n+1001));assertFalse(reportVisible(r,3,n-1))
 }
 @Test fun cursorAdvancesPastFailedFirstForty() {
  assertEquals((0..39).toList(),officialBatch(100,0));assertEquals((40..79).toList(),officialBatch(100,40));assertEquals(0,officialBatch(100,80)[20])
 }
 @Test fun responseLimitRejectsOversize() {
  assertEquals("ok",boundedOfficialText("ok".byteInputStream(),2))
  try { boundedOfficialText("too big".byteInputStream(),2);fail() } catch (_:IllegalArgumentException) { }
 }
 @Test fun weatherIsNotSecondSpokenCapSourceAndOtherHazardsRemain() {
  val a=CapAlert("id","sender","Flood","Severe","Warning","Area",1)
  assertFalse(capIsWeather(a));assertTrue(capIsWeather(a.copy(event="Thunderstorm")));assertTrue(capIsWeather(a.copy(categories=setOf("Met"))))
 }
}
