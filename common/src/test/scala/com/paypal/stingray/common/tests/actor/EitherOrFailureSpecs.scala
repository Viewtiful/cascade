package com.paypal.stingray.common.tests.actor

import org.specs2._
import org.scalacheck.Prop.{Exception => PropException, _}
import org.scalacheck.Arbitrary._
import com.paypal.stingray.common.actor._
import com.paypal.stingray.common.tests.scalacheck._
import akka.actor._

/**
 * Tests for implicit [[com.paypal.stingray.common.actor.EitherOrFailure]]
 */
class EitherOrFailureSpecs
  extends Specification
  with ScalaCheck { def is = s2"""

  EitherOrFailure is an implicit wrapper for Either objects to interoperate with Actors

  .orFailure should
    on an Either[E, A] that is Right, return the A value                          ${OrFailure.SuccessCase().ok}
    on an Either[E, A] that is Left, return Status.Failure                        ${OrFailure.FailureCase().fails}

  .orFailureWith should, given an Exception e
    on an Either[E, A] that is Right, return the A value                          ${OrFailureWith.SuccessCase().ok}
    on an Either[E, A] that is Left, return Status.Failure(e)                     ${OrFailureWith.FailureCase().fails}

  .orFailureWith should, given a function E => Exception
    on an Either[E, A] that is Right, return the A value                          ${OrFailureWithConversion.SuccessCase().ok}
    on an Either[E, A] that is Left, return Status.Failure with a converted left  ${OrFailureWithConversion.FailureCase().fails}

  """

  object OrFailure {

    case class SuccessCase() {
      def ok = forAll(arbitrary[String]) { s =>
        Right(s).orFailure must beEqualTo(s)
      }
    }
    case class FailureCase() {
      def fails = forAll(arbitrary[Exception]) { e =>
        Left(e).orFailure must beEqualTo(Status.Failure)
      }
    }
  }

  object OrFailureWith {

    case class SuccessCase() {
      def ok = forAll(arbitrary[String], arbitrary[Exception]) { (s, e) =>
        Right(s).orFailureWith(e) must beEqualTo(s)
      }
    }
    case class FailureCase() {
      def fails = forAll(arbitrary[Exception]) { e =>
        Left(new Exception("incorrect")).orFailureWith(e) must beEqualTo(Status.Failure(e))
      }
    }
  }

  object OrFailureWithConversion {

    private case class ConvertedException(m: String) extends Exception(m)

    case class SuccessCase() {
      def ok = forAll(arbitrary[Int]) { i =>
        Right[String, Int](i).orFailureWith(new Exception(_)) must beEqualTo(i)
      }
    }
    case class FailureCase() {
      def fails = forAll(arbitrary[String]) { s =>
        Left[String, Int](s).orFailureWith(ConvertedException(_)) must beEqualTo(Status.Failure(ConvertedException(s)))
      }
    }
  }

}
