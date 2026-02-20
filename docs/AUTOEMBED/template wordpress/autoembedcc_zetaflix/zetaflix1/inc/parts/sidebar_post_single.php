<?php 
$sidebar = zeta_get_option('sidebar_display');
$blog = zeta_get_option('pageblog');
if($sidebar == 1){
	echo '<aside>';
	?>
	<?php 


	if($page_id == $blog){
			echo 'test';
		}elseif(is_front_page() || is_home()) {		
			dynamic_sidebar('sidebar-home');
		}elseif(is_singular('movies')){			
			dynamic_sidebar('sidebar-tvshows');
		}elseif(is_singular('seasons')){
			dynamic_sidebar('sidebar-seasons');
		}else				?>
  <div class="sidebar-module">
    <div class="sidebar-title"><span>Trending</span></div>
    <div class="sidebar-content">
      <div class="top-listing">
        <ul class="top-list-nav">
          <li class="active"><a data-top="today">Today</a></li>
          <li><a data-top="week">Week</a></li>
          <li><a data-top="month">Month</a></li>
        </ul>
        <ul class="top-list">
          <li>
            <div class="top-item">
              <div class="top-item-left">
                <span class="top-rank">1</span>
                <div class="top-poster">
                  <div class="top-img" style="background-image: url('assets/img/thumb/poster/zsjl.png');"></div>
                </div>
              </div>
              <div class="top-item-right">
                <div class="top-row">
                  <span class="top-name">Zack Snyder's Justice League</span>
                </div>
                <div class="top-row">
                  <span class="top-year">2021</span> 
                  <span class="top-sep"></span> 
                  <span class="top-runtime">127 min</span>
                </div>
                <div class="top-row">
                  <div class="top-rating"> 
                   <div class="top-rating-stars">
                     <i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i>
                   </div> 
                   <span class="top-rating-average">(8.1)</span>
                 </div>
               </div>
                <div class="top-row">
                  <div class="top-view"><a href="#"><span class="top-watch-btn">Watch Movie</span></a></div>
                </div>
              </div>
            </div>
          </li>
          <li>
            <div class="top-item">
              <div class="top-item-left">
                <span class="top-rank">2</span>
                <div class="top-poster"><img data-original="assets/img/thumb/poster/nwh.jpg" class="lazy thumb mli-thumb" alt="Spider-Man: No Way Home" src="assets/img/thumb/poster/nwh.jpg"></div>
              </div>
              <div class="top-item-right">
                <div class="top-row">
                  <span class="top-name">Spider-Man: No Way Home</span>
                </div>
                <div class="top-row">
                  <span class="top-year">2021</span> 
                  <span class="top-sep"></span> 
                  <span class="top-runtime">127 min</span>
                </div>
                <div class="top-row">
                  <div class="top-rating"> 
                   <div class="top-rating-stars">
                    <i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i>
                   </div> 
                   <span class="top-rating-average">(8.1)</span>
                 </div>
               </div>
                <div class="top-row">
                  <div class="top-view"><a href="#"><span class="top-watch-btn">Watch Movie</span></a></div>
                </div>
              </div>
            </div>
          </li>
          <li>
            <div class="top-item">
              <div class="top-item-left">
                <span class="top-rank">3</span>
                <div class="top-poster"><img data-original="assets/img/thumb/poster/aotd.jpg" class="lazy thumb mli-thumb" alt="Zack Snyder's Justice League" src="assets/img/thumb/poster/aotd.jpg"></div>
              </div>
              <div class="top-item-right">
                <div class="top-row">
                  <span class="top-name">Army of the Dead</span>
                </div>
                <div class="top-row">
                  <span class="top-year">2021</span> 
                  <span class="top-sep"></span> 
                  <span class="top-runtime">127 min</span>
                </div>
                <div class="top-row">
                  <div class="top-rating"> 
                   <div class="top-rating-stars">
                     <i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i>
                   </div> 
                   <span class="top-rating-average">(8.1)</span>
                 </div>
               </div>
                <div class="top-row">
                  <div class="top-view"><a href="#"><span class="top-watch-btn">Watch Movie</span></a></div>
                </div>
              </div>
            </div>
          </li>
          <li>
            <div class="top-item">
              <div class="top-item-left">
                <span class="top-rank">4</span>
                <div class="top-poster"><img data-original="assets/img/thumb/poster/tm.jpg" class="lazy thumb mli-thumb" alt="Zack Snyder's Justice League" src="assets/img/thumb/poster/tm.jpg"></div>
              </div>
              <div class="top-item-right">
                <div class="top-row">
                  <span class="top-name">The Matrix: Resurrection</span>
                </div>
                <div class="top-row">
                  <span class="top-year">2021</span> 
                  <span class="top-sep"></span> 
                  <span class="top-runtime">127 min</span>
                </div>
                <div class="top-row">
                  <div class="top-rating"> 
                   <div class="top-rating-stars">
                     <i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i>
                   </div> 
                   <span class="top-rating-average">(8.1)</span>
                 </div>
               </div>
                <div class="top-row">
                  <div class="top-view"><a href="#"><span class="top-watch-btn">Watch Movie</span></a></div>
                </div>
              </div>
            </div>
          </li>
          <li>
            <div class="top-item">
              <div class="top-item-left">
                <span class="top-rank">5</span>
                <div class="top-poster"><img data-original="assets/img/thumb/poster/et.jpg" class="lazy thumb mli-thumb" alt="Zack Snyder's Justice League" src="assets/img/thumb/poster/et.jpg"></div>
              </div>
              <div class="top-item-right">
                <div class="top-row">
                  <span class="top-name">Eternals</span>
                </div>
                <div class="top-row">
                  <span class="top-year">2021</span> 
                  <span class="top-sep"></span> 
                  <span class="top-runtime">127 min</span>
                </div>
                <div class="top-row">
                  <div class="top-rating"> 
                   <div class="top-rating-stars">
                     <i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i><i class="fa-solid fa-star"></i>
                   </div> 
                   <span class="top-rating-average">(8.1)</span>
                 </div>
               </div>
                <div class="top-row">
                  <div class="top-view"><a href="#"><span class="top-watch-btn">Watch Movie</span></a></div>
                </div>
              </div>
            </div>
          </li>
        </ul>
      </div>
    </div>
  </div>
  <div class="clearfix"></div>
	<?php echo '</aside>';
}

?>