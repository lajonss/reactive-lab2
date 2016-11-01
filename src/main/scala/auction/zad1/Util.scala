package auction.zad1

import java.util.Random

object Util {
  implicit def intWithTimes(n: Int) = new {
    def times(f: => Unit) = 1 to n foreach {_ => f}
  }

  final val rand = new Random(System.currentTimeMillis())

  final val brands = Array("Polonez", "Warszawa", "Syrena", "Jelcz", "Star")
  final val engines = Array("V6", "V8", "126A2", "VM Turbodiesel")
  final val capacities = Array("2.0", "3.0", "2.1", "5.0", "2.1", "1.0", "0.9", "1.2")

  def getRandomItem(): String =
    brands(rand.nextInt(brands.length)) + " " +
    engines(rand.nextInt(engines.length)) + " " +
    capacities(rand.nextInt(capacities.length))

  def getRandomKeyword(): String =
    if(Math.random() < 0.3) {
      brands(rand.nextInt(brands.length))
    } else if(Math.random < 0.5) {
      engines(rand.nextInt(engines.length))
    } else {
      capacities(rand.nextInt(capacities.length))
    }

  def getRandomAmountOfMoney(): Int = rand.nextInt(200)
}
