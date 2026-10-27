package pi.integrated.payment.repository;

import pi.integrated.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.paymentStatus = :status")
    Double calculateTotalRevenueByStatus(@Param("status") String status);

    /** Enrollment check used by course-service (Ask the course). */
    boolean existsByUserIdAndCourseIdAndPaymentStatus(Long userId, Long courseId, String paymentStatus);
}
