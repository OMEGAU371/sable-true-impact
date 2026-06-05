# Physics Invariants

Evidence levels: **[PC]** physics theory · **[SP]** source-proven · **[IT]** inferred · **[UC]** unconfirmed

These invariants constrain how observed data may be interpreted.
Violating them produces physically incorrect energy estimates.

---

## 1. Single-body kinetic energy **[PC]**

$$E_k = \tfrac{1}{2} m \,|\mathbf{v}_{cm}|^2 + \tfrac{1}{2} \boldsymbol{\omega}^\top \mathbf{I} \boldsymbol{\omega}$$

- $m$ in kpg; $\mathbf{v}_{cm}$ from `getLinearVelocity()` (unit **[UC: T-7]**);  
  $\boldsymbol{\omega}$ from `getAngularVelocity()` (unit **[UC: T-7]**).
- **[C7-codex]** Do NOT assign the full $\tfrac{1}{2}mv^2$ to each contact point.

---

## 2. Contact-point velocity **[PC]**

$$\mathbf{v}_P^A = \mathbf{v}_{cm}^A + \boldsymbol{\omega}^A \times \mathbf{r}^A$$

where $\mathbf{r}^A = \mathbf{p}_{contact}^{WORLD} - \mathbf{COM}^A_{WORLD}$.

Conversion for Rapier data: `worldPoint = Q · localPointA + position` **[SP: RapierPhysicsPipeline.java:254]**.  
Then `r_A = worldPoint − position = Q · localPointA`.

---

## 3. Normal closing velocity **[PC]**

$$v_n = (\mathbf{v}_P^A - \mathbf{v}_P^B) \cdot \hat{\mathbf{n}}$$

Direction convention of $\hat{\mathbf{n}}$ (from `localNormalA`): **[UC: T-6]**.  
Static contact: $v_n \approx 0$ — must NOT be reported as impact energy.

---

## 4. Single-sided effective mass **[PC + SP: MassData.java:33]**

$$K_A = \frac{1}{m_A} + (\mathbf{r}^A \times \hat{\mathbf{n}})^\top \cdot \mathbf{I}_A^{-1} \cdot (\mathbf{r}^A \times \hat{\mathbf{n}})$$

`getInverseNormalMass(position, direction)` computes $K_A$ directly.

---

## 5. Combined effective mass **[PC; C1-codex correction]**

$$\frac{1}{m_{eff}} = K_A + K_B \qquad \Rightarrow \qquad m_{eff} = \frac{1}{K_A + K_B}$$

For terrain B (static, infinite mass): $K_B = 0$, so $m_{eff} = 1/K_A$.

**Special case — contact at COM, rotational effects negligible:**  
$K_A = 1/m_A$, $K_B = 1/m_B$ → $m_{eff} = m_A m_B / (m_A + m_B)$.  
`min(mA, mB)` is only valid when one body is much heavier than the other.

**[C6-codex]** $\mathbf{r}^A$ and $\hat{\mathbf{n}}$ must be in the same coordinate space; both sides computed independently in their own body-local frames.

---

## 6. Available and dissipated energy **[PC; C7-codex]**

$$E_{available} = \tfrac{1}{2} \, m_{eff} \, v_n^2$$

$$E_{dissipated} = E_{available} \, (1 - e^2)$$

where $e$ = combined restitution coefficient.

$e$ per block: `PhysicsBlockPropertyHelper.getRestitution(state)` [SP], default 0.0 [PhysicsBlockPropertyTypes.java:32].  
Rapier combination rule for $e$: **[UC]** — whether Rapier uses min, max, multiply, or other is unknown.  
Multi-contact behaviour: **[UC]** — whether energy is shared across manifold contacts or independent per contact is unknown.

**[C3-codex]** $E_{dissipated}$ definition is independent of whether `applyForce` is force or impulse.  
The Rapier solver has already applied collision response; do NOT re-apply additional impulses based on this formula unless T-4 and T-3 confirm both the impulse semantics and the absence of double-application.

---

## 7. Forbidden operations **[PC + C7-codex]**

- Do NOT use `min(mA, mB)` as effective mass for non-central contacts.
- Do NOT distribute whole-body $\tfrac{1}{2}mv^2$ to individual contacts.
- Do NOT apply extra "reaction" impulses: Rapier constraint solver already enforces Newton's 3rd law.
- Do NOT report $v_n$ of a resting contact as impact energy.
- Do NOT assume `localNormalA` points from B to A without T-6 confirmation.
