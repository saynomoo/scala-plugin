import collection.Seq

class Blerg(val a: String)

implicit def SeqBlerg[M[X] <: Seq[X], A](l: M[A]) = ""
val seq: Seq[Int] = Seq(1)
/*start*/seq.a/*end*/
//String